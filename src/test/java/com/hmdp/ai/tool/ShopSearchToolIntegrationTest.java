package com.hmdp.ai.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ShopSearchToolIntegrationTest {
    @Resource
    private ShopSearchTool shopSearchTool;

    @Test
    void searchesXiAnCinemaByAreaAndTypeInsteadOfNameOnly() {
        String json = shopSearchTool.execute(
                Collections.<String, Object>singletonMap("keyword", "小寨看电影"), null);

        JSONArray results = JSONUtil.parseArray(json);
        assertTrue(results.stream().anyMatch(item -> ((Number) ((cn.hutool.json.JSONObject) item).get("id")).longValue() == 30L));
    }

    @Test
    void fallsBackToStructuredFiltersWhenKeywordIsOnlyARecommendationRequest() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", "适合一家三口");
        arguments.put("area", "小寨");
        arguments.put("type", "影院");

        JSONArray results = JSONUtil.parseArray(shopSearchTool.execute(arguments, null));
        assertTrue(results.stream().anyMatch(item -> ((Number) ((cn.hutool.json.JSONObject) item).get("id")).longValue() == 30L));
    }
}
