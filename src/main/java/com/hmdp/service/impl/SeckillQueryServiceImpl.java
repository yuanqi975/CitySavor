package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillStatus;
import com.hmdp.dto.SeckillVoucherDTO;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillQueryService;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class SeckillQueryServiceImpl implements ISeckillQueryService {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result list(String status) {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillVoucherDTO> result = new ArrayList<>();
        for (SeckillVoucherDTO voucher : seckillVoucherMapper.selectSeckillVouchers(status)) {
            stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_STOCK_KEY + voucher.getVoucherId(), String.valueOf(voucher.getStock()));
            voucher.setStatus(SeckillStatusResolver.resolve(voucher.getBeginTime(), voucher.getEndTime(), voucher.getStock(), now));
            if (status == null || status.trim().isEmpty() || "all".equalsIgnoreCase(status)
                    || voucher.getStatus().name().equalsIgnoreCase(status)) result.add(voucher);
        }
        return Result.ok(result);
    }
}
