package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherOrderPersistenceService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final ISeckillVoucherService seckillVoucherService;

    public VoucherOrderPersistenceService(VoucherOrderMapper voucherOrderMapper,
                                          ISeckillVoucherService seckillVoucherService) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.seckillVoucherService = seckillVoucherService;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean createOrder(VoucherOrder order) {
        if (orderExists(order.getUserId(), order.getVoucherId())) {
            return false;
        }

        boolean stockDeducted = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!stockDeducted) {
            throw new IllegalStateException("Database stock is insufficient for voucher "
                    + order.getVoucherId());
        }

        voucherOrderMapper.insert(order);
        return true;
    }

    public boolean orderExists(Long userId, Long voucherId) {
        Integer count = voucherOrderMapper.selectCount(
                new QueryWrapper<VoucherOrder>()
                        .eq("user_id", userId)
                        .eq("voucher_id", voucherId));
        return count != null && count > 0;
    }
}
