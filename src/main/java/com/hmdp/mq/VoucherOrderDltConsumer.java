package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.VoucherOrderPersistenceService;
import com.hmdp.service.VoucherOrderReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VoucherOrderDltConsumer {

    private final VoucherOrderPersistenceService persistenceService;
    private final VoucherOrderReservationService reservationService;

    public VoucherOrderDltConsumer(VoucherOrderPersistenceService persistenceService,
                                   VoucherOrderReservationService reservationService) {
        this.persistenceService = persistenceService;
        this.reservationService = reservationService;
    }

    @KafkaListener(
            topics = "${hmdp.kafka.voucher-order-dlt-topic}",
            groupId = "${hmdp.kafka.voucher-order-dlt-group}",
            containerFactory = "dltKafkaListenerContainerFactory")
    public void consume(VoucherOrder order, Acknowledgment acknowledgment) {
        if (persistenceService.orderExists(order.getUserId(), order.getVoucherId())) {
            log.warn("DLT order already exists in database; skip Redis compensation: orderId={}",
                    order.getId());
        } else {
            boolean compensated = reservationService.compensate(
                    order.getVoucherId(), order.getUserId(), order.getId());
            log.error("Voucher order reached DLT; Redis reservation compensated={}, orderId={}",
                    compensated, order.getId());
        }
        acknowledgment.acknowledge();
    }
}
