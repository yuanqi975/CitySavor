package com.hmdp.ai.tool;

import cn.hutool.json.JSONUtil;
import com.hmdp.ai.AiTool;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ShopVoucherTool implements AiTool {
    @Resource
    private IVoucherService voucherService;

    @Override
    public String name() {
        return "list_shop_vouchers";
    }

    @Override
    public String description() {
        return "查询指定店铺当前配置的优惠券和使用规则。";
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
        List<Voucher> vouchers = voucherService.query().eq("shop_id", shopId).list();
        return JSONUtil.toJsonStr(vouchers);
    }
}
