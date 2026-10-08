package com.hmdp.service.impl;

import com.hmdp.dto.SeckillStatus;

import java.time.LocalDateTime;

public final class SeckillStatusResolver {
    private SeckillStatusResolver() {
    }

    public static SeckillStatus resolve(LocalDateTime beginTime, LocalDateTime endTime, Integer stock, LocalDateTime now) {
        if (stock == null || stock < 1) return SeckillStatus.SOLD_OUT;
        if (beginTime != null && beginTime.isAfter(now)) return SeckillStatus.NOT_STARTED;
        if (endTime != null && endTime.isBefore(now)) return SeckillStatus.ENDED;
        return SeckillStatus.ACTIVE;
    }
}
