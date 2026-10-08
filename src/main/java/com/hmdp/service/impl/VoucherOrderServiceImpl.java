package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.VoucherOrderProducer;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.VoucherOrderReservationService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class VoucherOrderServiceImpl
        extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final VoucherOrderProducer voucherOrderProducer;
    private final VoucherOrderReservationService reservationService;

    public VoucherOrderServiceImpl(RedisIdWorker redisIdWorker,
                                   StringRedisTemplate stringRedisTemplate,
                                   VoucherOrderProducer voucherOrderProducer,
                                   VoucherOrderReservationService reservationService) {
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.voucherOrderProducer = voucherOrderProducer;
        this.reservationService = reservationService;
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        if (result == null) {
            return Result.fail("秒杀服务繁忙，请稍后重试");
        }
        if (result != 0L) {
            return Result.fail(result == 1L ? "库存不足" : "不能重复下单");
        }

        VoucherOrder order = new VoucherOrder()
                .setId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId);
        try {
            voucherOrderProducer.send(order);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka send outcome unknown after interruption, orderId={}", orderId, e);
            return Result.fail("订单投递状态待确认，请联系管理员核查订单号：" + orderId);
        } catch (TimeoutException | ExecutionException e) {
            log.error("Kafka send outcome unknown, keep Redis reservation, orderId={}",
                    orderId, e);
            return Result.fail("订单投递状态待确认，请联系管理员核查订单号：" + orderId);
        } catch (Exception e) {
            compensateAfterSendFailure(order, e);
            return Result.fail("订单消息发送失败，请稍后重试");
        }

        return Result.ok(orderId);
    }

    private void compensateAfterSendFailure(VoucherOrder order, Exception sendException) {
        try {
            boolean compensated = reservationService.compensate(
                    order.getVoucherId(), order.getUserId(), order.getId());
            log.error("Kafka send failed; Redis reservation compensated={}, orderId={}",
                    compensated, order.getId(), sendException);
        } catch (Exception compensationException) {
            log.error("Kafka send and Redis compensation both failed, orderId={}",
                    order.getId(), compensationException);
        }
    }
}
