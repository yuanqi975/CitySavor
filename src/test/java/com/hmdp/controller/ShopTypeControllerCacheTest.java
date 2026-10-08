package com.hmdp.controller;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ShopTypeControllerCacheTest {

    private static final String CACHE_KEY = "cache:shop:type";

    @Resource
    private ShopTypeController shopTypeController;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    @AfterEach
    void clearCache() {
        stringRedisTemplate.delete(CACHE_KEY);
    }

    @Test
    void queryTypeListReturnsTheCachedListWhenItExists() {
        ShopType cachedType = new ShopType()
                .setId(999L)
                .setName("缓存分类")
                .setIcon("/types/cache.png")
                .setSort(1);
        stringRedisTemplate.opsForValue().set(CACHE_KEY, JSONUtil.toJsonStr(Collections.singletonList(cachedType)));

        Result result = shopTypeController.queryTypeList();

        assertThat(result.getSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<ShopType> types = (List<ShopType>) result.getData();
        assertThat(types)
                .extracting(ShopType::getId, ShopType::getName)
                .containsExactly(tuple(999L, "缓存分类"));
    }

    @Test
    void queryTypeListCachesDatabaseResultWhenTheCacheIsEmpty() {
        Result result = shopTypeController.queryTypeList();

        assertThat(result.getSuccess()).isTrue();
        assertThat(stringRedisTemplate.opsForValue().get(CACHE_KEY)).isNotBlank();
    }
}
