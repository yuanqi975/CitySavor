package com.hmdp.ai.tool;

import cn.hutool.json.JSONUtil;
import com.hmdp.ai.AiTool;
import com.hmdp.dto.ShopCommentDTO;
import com.hmdp.dto.ShopCommentPageDTO;
import com.hmdp.service.IShopCommentService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ShopCommentTool implements AiTool {
    @Resource
    private IShopCommentService shopCommentService;

    @Override
    public String name() {
        return "list_shop_comments";
    }

    @Override
    public String description() {
        return "查询指定店铺的评分摘要和近期评价，用于回答店铺口碑、差评、环境或服务体验问题。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("shopId", ShopSearchTool.property("integer", "店铺 ID"));
        properties.put("rating", ShopSearchTool.property("integer", "可选，按 1 到 5 星筛选"));
        properties.put("limit", ShopSearchTool.property("integer", "最多返回 10 条评价"));
        return ShopSearchTool.schema(properties, Arrays.asList("shopId"));
    }

    @Override
    public String execute(Map<String, Object> arguments, Long userId) {
        Long shopId = Long.valueOf(String.valueOf(arguments.get("shopId")));
        int limit = Math.min(Math.max(number(arguments.get("limit"), 10), 1), 10);
        int rating = number(arguments.get("rating"), 0);
        ShopCommentPageDTO page = shopCommentService.getCommentPage(shopId, 1, 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageRating", page.getAverageRating());
        result.put("total", page.getTotal());
        result.put("ratingDistribution", page.getRatingDistribution());
        result.put("comments", shopCommentService.getRecentComments(shopId, rating, limit));
        return JSONUtil.toJsonStr(result);
    }

    private int number(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
