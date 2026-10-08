package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.VoucherOrderPersistenceService;
import com.hmdp.service.VoucherOrderReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderDltConsumerTest {

    @Test
    void compensatesWhenOrderDoesNotExist() {
        VoucherOrderPersistenceService persistenceService =
                mock(VoucherOrderPersistenceService.class);
        VoucherOrderReservationService reservationService =
                mock(VoucherOrderReservationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        VoucherOrder order = order();
        when(persistenceService.orderExists(7L, 9L)).thenReturn(false);

        new VoucherOrderDltConsumer(persistenceService, reservationService)
                .consume(order, acknowledgment);

        verify(reservationService).compensate(9L, 7L, 101L);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotCompensateAnOrderAlreadyCommittedToDatabase() {
        VoucherOrderPersistenceService persistenceService =
                mock(VoucherOrderPersistenceService.class);
        VoucherOrderReservationService reservationService =
                mock(VoucherOrderReservationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        VoucherOrder order = order();
        when(persistenceService.orderExists(7L, 9L)).thenReturn(true);

        new VoucherOrderDltConsumer(persistenceService, reservationService)
                .consume(order, acknowledgment);

        verify(reservationService, never()).compensate(9L, 7L, 101L);
        verify(acknowledgment).acknowledge();
    }

    private VoucherOrder order() {
        return new VoucherOrder().setId(101L).setUserId(7L).setVoucherId(9L);
    }
}
