package com.hmdp.mq;

import com.hmdp.config.KafkaTopicProperties;
import com.hmdp.entity.VoucherOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class VoucherOrderProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final KafkaTopicProperties properties;

    public VoucherOrderProducer(KafkaTemplate<Object, Object> kafkaTemplate,
                                KafkaTopicProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void send(VoucherOrder order) throws Exception {
        kafkaTemplate.send(
                        properties.getVoucherOrderTopic(),
                        String.valueOf(order.getVoucherId()),
                        order)
                .get(properties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
    }
}
