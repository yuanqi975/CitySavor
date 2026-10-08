package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mq.VoucherOrderProducer;
import com.hmdp.service.VoucherOrderReservationService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherOrderServiceKafkaTest {

    private RedisIdWorker redisIdWorker;
    private StringRedisTemplate redisTemplate;
    private VoucherOrderProducer producer;
    private VoucherOrderReservationService reservationService;
    private VoucherOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        redisIdWorker = mock(RedisIdWorker.class);
        redisTemplate = mock(StringRedisTemplate.class);
        producer = mock(VoucherOrderProducer.class);
        reservationService = mock(VoucherOrderReservationService.class);
        service = new VoucherOrderServiceImpl(
                redisIdWorker, redisTemplate, producer, reservationService);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
        when(redisIdWorker.nextId("order")).thenReturn(101L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void compensatesRedisWhenKafkaSendFails() throws Exception {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.emptyList()),
                eq("9"), eq("7"), eq("101"))).thenReturn(0L);
        doThrow(new IllegalStateException("Kafka unavailable"))
                .when(producer).send(any(VoucherOrder.class));
        when(reservationService.compensate(9L, 7L, 101L)).thenReturn(true);

        Result result = service.seckillVoucher(9L);

        assertThat(result.getSuccess()).isFalse();
        verify(reservationService).compensate(9L, 7L, 101L);
    }

    @Test
    void doesNotSendWhenRedisRejectsRequest() throws Exception {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.emptyList()),
                eq("9"), eq("7"), eq("101"))).thenReturn(1L);

        Result result = service.seckillVoucher(9L);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("库存不足");
        verify(producer, never()).send(any(VoucherOrder.class));
    }

    @Test
    void keepsReservationWhenKafkaOutcomeIsUnknown() throws Exception {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.emptyList()),
                eq("9"), eq("7"), eq("101"))).thenReturn(0L);
        doThrow(new TimeoutException("Kafka acknowledgment timed out"))
                .when(producer).send(any(VoucherOrder.class));

        Result result = service.seckillVoucher(9L);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMsg()).contains("101");
        verify(reservationService, never()).compensate(9L, 7L, 101L);
    }
}
