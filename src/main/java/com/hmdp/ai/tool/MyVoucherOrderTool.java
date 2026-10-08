package com.hmdp.ai.tool;

import cn.hutool.json.JSONUtil;
import com.hmdp.ai.AiTool;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MyVoucherOrderTool implements AiTool {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public String name() {
        return "list_my_voucher_orders";
    }

    @Override
    public String description() {
        return "查询当前登录用户最近的代金券订单状态。只能查询当前用户，不能查询其他用户。";
    }

    @Override
    public Map<String, Object> parameters() {
        return ShopSearchTool.schema(new LinkedHashMap<String, Object>(), Collections.<String>emptyList());
    }

    @Override
    public String execute(Map<String, Object> arguments, Long userId) {
        List<VoucherOrder> orders = voucherOrderService.query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT 10")
                .list();
        return JSONUtil.toJsonStr(orders);
    }
}
