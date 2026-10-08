package com.hmdp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.KafkaException;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.TimeUnit;

@Configuration
public class KafkaConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    @Bean
    public NewTopic voucherOrderTopic(KafkaTopicProperties properties) {
        return new NewTopic(properties.getVoucherOrderTopic(), PARTITIONS, REPLICAS);
    }

    @Bean
    public NewTopic voucherOrderDltTopic(KafkaTopicProperties properties) {
        return new NewTopic(properties.getVoucherOrderDltTopic(), PARTITIONS, REPLICAS);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaTopicProperties properties) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        properties.getVoucherOrderDltTopic(), record.partition())) {
            @Override
            protected void publish(ProducerRecord<Object, Object> outRecord,
                                   KafkaOperations<Object, Object> operations) {
                try {
                    operations.send(outRecord).get(
                            properties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new KafkaException("Interrupted while publishing to DLT", e);
                } catch (Exception e) {
                    throw new KafkaException("Failed to publish record to DLT", e);
                }
            }
        };
        SeekToCurrentErrorHandler errorHandler = new SeekToCurrentErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3L));
        errorHandler.setCommitRecovered(true);
        factory.setErrorHandler(errorHandler);
        return factory;
    }

    @Bean(name = "dltKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<Object, Object> dltKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setErrorHandler(new SeekToCurrentErrorHandler(
                new FixedBackOff(5000L, Long.MAX_VALUE)));
        return factory;
    }
}
