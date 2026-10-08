package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VoucherOrderPersistenceServiceTest {

    @Test
    void duplicateOrderDoesNotDeductDatabaseStockOrInsertAnotherOrder() {
        VoucherOrderMapper orderMapper = mock(VoucherOrderMapper.class);
        ISeckillVoucherService voucherService = mock(ISeckillVoucherService.class);
        when(orderMapper.selectCount(any(QueryWrapper.class))).thenReturn(1);
        VoucherOrder order = new VoucherOrder()
                .setId(101L).setUserId(7L).setVoucherId(9L);

        boolean created = new VoucherOrderPersistenceService(orderMapper, voucherService)
                .createOrder(order);

        assertThat(created).isFalse();
        verify(orderMapper, never()).insert(order);
        verifyNoInteractions(voucherService);
    }
}
