package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.VoucherOrderPersistenceService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderConsumerTest {

    @Test
    void acknowledgesOnlyAfterPersistenceReturnsSuccessfully() {
        VoucherOrderPersistenceService persistenceService =
                mock(VoucherOrderPersistenceService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        VoucherOrder order = order();
        when(redissonClient.getLock("lock:order:7")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(persistenceService.createOrder(order)).thenReturn(true);

        new VoucherOrderConsumer(persistenceService, redissonClient)
                .consume(order, acknowledgment);

        InOrder calls = inOrder(persistenceService, acknowledgment);
        calls.verify(persistenceService).createOrder(order);
        calls.verify(acknowledgment).acknowledge();
        verify(lock).unlock();
    }

    @Test
    void doesNotAcknowledgeWhenPersistenceFails() {
        VoucherOrderPersistenceService persistenceService =
                mock(VoucherOrderPersistenceService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        VoucherOrder order = order();
        when(redissonClient.getLock("lock:order:7")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(persistenceService.createOrder(order))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> new VoucherOrderConsumer(persistenceService, redissonClient)
                        .consume(order, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
        verify(lock).unlock();
    }

    private VoucherOrder order() {
        return new VoucherOrder().setId(101L).setUserId(7L).setVoucherId(9L);
    }
}
