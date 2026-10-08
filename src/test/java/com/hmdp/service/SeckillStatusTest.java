package com.hmdp.service;

import com.hmdp.dto.SeckillStatus;
import com.hmdp.service.impl.SeckillStatusResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillStatusTest {

    @Test
    void resolvesNotStartedActiveEndedAndSoldOutStates() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 12, 0);
        assertThat(SeckillStatusResolver.resolve(now.plusMinutes(1), now.plusHours(1), 3, now)).isEqualTo(SeckillStatus.NOT_STARTED);
        assertThat(SeckillStatusResolver.resolve(now.minusMinutes(1), now.plusHours(1), 3, now)).isEqualTo(SeckillStatus.ACTIVE);
        assertThat(SeckillStatusResolver.resolve(now.minusHours(2), now.minusMinutes(1), 3, now)).isEqualTo(SeckillStatus.ENDED);
        assertThat(SeckillStatusResolver.resolve(now.minusMinutes(1), now.plusHours(1), 0, now)).isEqualTo(SeckillStatus.SOLD_OUT);
    }
}
