package com.hmdp.ai.tool;

import cn.hutool.json.JSONUtil;
import com.hmdp.ai.AiTool;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ShopDetailTool implements AiTool {
    @Resource
    private IShopService shopService;

    @Override
    public String name() {
        return "get_shop_detail";
    }

    @Override
    public String description() {
        return "根据店铺 ID 查询营业时间、地址、评分和人均消费。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("shopId", ShopSearchTool.property("integer", "店铺 ID"));
        return ShopSearchTool.schema(properties, Arrays.asList("shopId"));
    }

    @Override
    public String execute(Map<String, Object> arguments, Long userId) {
        Long shopId = Long.valueOf(String.valueOf(arguments.get("shopId")));
        Shop shop = shopService.getById(shopId);
        return shop == null ? "未找到该店铺" : JSONUtil.toJsonStr(shop);
    }
}
