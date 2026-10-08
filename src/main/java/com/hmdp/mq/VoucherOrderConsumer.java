package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.VoucherOrderPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VoucherOrderConsumer {

    private final VoucherOrderPersistenceService persistenceService;
    private final RedissonClient redissonClient;

    public VoucherOrderConsumer(VoucherOrderPersistenceService persistenceService,
                                RedissonClient redissonClient) {
        this.persistenceService = persistenceService;
        this.redissonClient = redissonClient;
    }

    @KafkaListener(
            topics = "${hmdp.kafka.voucher-order-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(VoucherOrder order, Acknowledgment acknowledgment) {
        RLock lock = redissonClient.getLock("lock:order:" + order.getUserId());
        if (!lock.tryLock()) {
            throw new IllegalStateException("Could not acquire order lock for user "
                    + order.getUserId());
        }

        try {
            boolean created = persistenceService.createOrder(order);
            if (created) {
                log.info("Voucher order persisted: orderId={}", order.getId());
            } else {
                log.info("Duplicate voucher order ignored: orderId={}", order.getId());
            }
        } finally {
            lock.unlock();
        }

        // createOrder() has returned through the transactional proxy, so its transaction committed.
        acknowledgment.acknowledge();
    }
}
