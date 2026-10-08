package com.hmdp.ai.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.ai.AiTool;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ShopSearchTool implements AiTool {
    private static final int MAX_RESULT_SIZE = 5;

    @Resource
    private IShopService shopService;

    @Override
    public String name() {
        return "search_shops";
    }

    @Override
    public String description() {
        return "搜索平台内店铺，最多返回五家。查询本身会匹配店铺名称、商圈和地址。"
                + "当用户表达地点和业态（例如“小寨看电影”）时，必须把 area 填为“小寨”、type 填为“电影”，"
                + "keyword 只填写明确的店名或剩余关键词，不要把整句自然语言放入 keyword。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", property("string", "可选：明确店名或关键词；会匹配名称、商圈和地址"));
        properties.put("area", property("string", "可选：商圈或地点，例如小寨、曲江、高新"));
        properties.put("type", property("string", "可选：业态，例如电影、KTV、咖啡、健身、美食"));
        return schema(properties, Collections.<String>emptyList());
    }

    @Override
    public String execute(Map<String, Object> arguments, Long userId) {
        SearchCriteria criteria = SearchCriteria.from(
                text(arguments.get("keyword")),
                text(arguments.get("area")),
                text(arguments.get("type")),
                null
        );
        if (criteria.isEmpty()) {
            return "请提供店名、商圈、地址或店铺类型中的至少一项";
        }

        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(criteria.getTextKeyword())) {
            query.and(wrapper -> wrapper.like(Shop::getName, criteria.getTextKeyword())
                    .or().like(Shop::getArea, criteria.getTextKeyword())
                    .or().like(Shop::getAddress, criteria.getTextKeyword()));
        }
        if (StrUtil.isNotBlank(criteria.getArea())) {
            query.and(wrapper -> wrapper.like(Shop::getArea, criteria.getArea())
                    .or().like(Shop::getAddress, criteria.getArea()));
        }
        Long typeId = typeId(criteria.getTypeKeyword());
        if (typeId != null) {
            query.eq(Shop::getTypeId, typeId);
        }
        List<Shop> shops = shopService.list(query.orderByDesc(Shop::getScore)
                .orderByDesc(Shop::getComments)
                .last("LIMIT " + MAX_RESULT_SIZE));
        return JSONUtil.toJsonStr(shops);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long typeId(String type) {
        if ("美食".equals(type)) return 1L;
        if ("KTV".equals(type)) return 2L;
        if ("咖啡".equals(type)) return 3L;
        if ("健身".equals(type)) return 4L;
        if ("丽人".equals(type)) return 5L;
        if ("电影".equals(type)) return 7L;
        if ("酒吧".equals(type)) return 8L;
        if ("养生".equals(type)) return 9L;
        if ("亲子".equals(type)) return 10L;
        return null;
    }

    public static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    public static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * Converts the small, fixed vocabulary used by the customer-service tool
     * into query conditions. The tool remains deterministic: it does not use
     * an embedding model or alter data in the shop tables.
     */
    public static final class SearchCriteria {
        private static final String[] COMMON_AREAS = {
                "小寨", "曲江", "钟楼", "鼓楼", "高新", "南门", "永宁门", "大雁塔", "雁塔",
                "未央", "长安", "莲湖", "碑林", "灞桥", "浐灞"
        };
        private final String textKeyword;
        private final String area;
        private final String typeKeyword;

        private SearchCriteria(String textKeyword, String area, String typeKeyword) {
            this.textKeyword = blankToNull(textKeyword);
            this.area = blankToNull(area);
            this.typeKeyword = blankToNull(typeKeyword);
        }

        public static SearchCriteria from(String keyword, String area, String type, Integer ignoredLimit) {
            String normalizedKeyword = blankToNull(keyword);
            String normalizedArea = blankToNull(area);
            String normalizedType = canonicalType(type);
            if (normalizedType == null) {
                normalizedType = canonicalType(normalizedKeyword);
            }
            if (normalizedArea == null) {
                normalizedArea = areaFromSentence(normalizedKeyword);
            }
            if (normalizedType != null && normalizedKeyword != null && normalizedArea == null) {
                normalizedKeyword = removeTypeWords(normalizedKeyword);
            }
            if (normalizedArea != null && normalizedKeyword != null && looksLikeSentence(normalizedKeyword)) {
                normalizedKeyword = removeTypeWords(normalizedKeyword)
                        .replace(normalizedArea, "");
                normalizedKeyword = removeNaturalLanguageWords(normalizedKeyword);
            }
            return new SearchCriteria(normalizedKeyword, normalizedArea, normalizedType);
        }

        public static String canonicalType(String value) {
            if (StrUtil.isBlank(value)) return null;
            String lower = value.toLowerCase();
            if (lower.contains("电影") || lower.contains("影院") || lower.contains("看电影")) return "电影";
            if (lower.contains("ktv") || value.contains("唱歌") || value.contains("k歌")) return "KTV";
            if (value.contains("咖啡")) return "咖啡";
            if (value.contains("健身") || value.contains("运动")) return "健身";
            if (value.contains("美发") || value.contains("美容") || value.contains("丽人")) return "丽人";
            if (value.contains("酒吧")) return "酒吧";
            if (value.contains("亲子") || value.contains("儿童")) return "亲子";
            if (value.contains("按摩") || value.contains("足疗") || value.contains("养生") || value.contains("SPA")) return "养生";
            if (value.contains("火锅") || value.contains("美食") || value.contains("吃饭") || value.contains("餐") || value.contains("小吃")) return "美食";
            return null;
        }

        private static String removeTypeWords(String value) {
            return value.replace("看电影", "").replace("电影院", "").replace("电影", "").replace("影院", "")
                    .replace("KTV", "").replace("ktv", "").replace("唱歌", "").replace("K歌", "").replace("k歌", "")
                    .replace("咖啡", "").replace("健身", "").replace("运动", "").replace("火锅", "")
                    .replace("美食", "").replace("吃饭", "").replace("餐饮", "").trim();
        }

        private static String areaFromSentence(String value) {
            if (StrUtil.isBlank(value) || !looksLikeSentence(value)) return null;
            for (String area : COMMON_AREAS) {
                if (value.contains(area)) return area;
            }
            return null;
        }

        private static boolean looksLikeSentence(String value) {
            return value.contains("想") || value.contains("附近") || value.contains("哪里")
                    || value.contains("推荐") || value.contains("适合") || value.contains("一家")
                    || value.contains("能去") || value.contains("帮我") || value.contains("请问");
        }

        private static String removeNaturalLanguageWords(String value) {
            return value.replace("一家三口", "").replace("一家人", "").replace("想去", "")
                    .replace("附近", "").replace("哪里", "").replace("推荐", "")
                    .replace("适合", "").replace("能去", "").replace("帮我", "")
                    .replace("请问", "").replace("有什么", "").replace("可以", "")
                    .replace("聚餐", "").replace("约会", "").replace("带娃", "")
                    .replace("，", "").replace(",", "").trim();
        }

        private static String blankToNull(String value) {
            return StrUtil.isBlank(value) ? null : value.trim();
        }

        public boolean isEmpty() {
            return textKeyword == null && area == null && typeKeyword == null;
        }

        public String getTextKeyword() {
            return textKeyword;
        }

        public String getArea() {
            return area;
        }

        public String getTypeKeyword() {
            return typeKeyword;
        }
    }
}
