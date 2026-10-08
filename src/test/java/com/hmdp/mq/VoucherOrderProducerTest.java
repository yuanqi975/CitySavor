package com.hmdp.mq;

import com.hmdp.config.KafkaTopicProperties;
import com.hmdp.entity.VoucherOrder;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderProducerTest {

    @Test
    void sendsWithVoucherIdAsKeyAndWaitsForBrokerResult() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaTopicProperties properties = new KafkaTopicProperties();
        properties.setSendTimeoutSeconds(1L);
        VoucherOrderProducer producer = new VoucherOrderProducer(kafkaTemplate, properties);
        VoucherOrder order = new VoucherOrder()
                .setId(101L)
                .setUserId(7L)
                .setVoucherId(9L);
        SettableListenableFuture<SendResult<Object, Object>> future =
                new SettableListenableFuture<>();
        future.set(null);
        when(kafkaTemplate.send("voucher-order-topic", "9", order)).thenReturn(future);

        producer.send(order);

        verify(kafkaTemplate).send("voucher-order-topic", "9", order);
    }
}
