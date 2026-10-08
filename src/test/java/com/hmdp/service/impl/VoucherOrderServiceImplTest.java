package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherOrderServiceImplTest {

    @Test
    void seckillScriptNoLongerWritesRedisStream() throws IOException {
        String script = readResource("/seckill.lua");

        assertThat(script.toLowerCase()).doesNotContain("xadd", "stream.orders");
        assertThat(script.toLowerCase()).contains("incrby", "sadd");
    }

    @Test
    void compensationScriptIsGuardedForIdempotency() throws IOException {
        String script = readResource("/seckill-compensate.lua");

        assertThat(script.toLowerCase()).contains("hget", "hdel", "sismember", "srem", "incrby");
    }

    @Test
    void orderTableHasBusinessUniqueIndex() throws IOException {
        String schema = readResource("/db/hmdp.sql");
        String orderTable = schema.substring(schema.indexOf("CREATE TABLE `tb_voucher_order`"));

        assertThat(orderTable).contains("UNIQUE INDEX `uk_user_voucher`(`user_id`, `voucher_id`)");
    }

    private String readResource(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing resource " + name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
