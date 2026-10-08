package com.hmdp.service;

import com.hmdp.ai.tool.ShopSearchTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopSearchCriteriaTest {

    @Test
    void extractsAreaAndCinemaTypeFromNaturalLanguageKeyword() {
        ShopSearchTool.SearchCriteria criteria = ShopSearchTool.SearchCriteria.from("小寨看电影", null, null, null);

        assertEquals("小寨", criteria.getTextKeyword());
        assertEquals("电影", criteria.getTypeKeyword());
    }

    @Test
    void dropsGenericRecommendationWordsWhenAreaAndTypeAreProvidedByAgent() {
        ShopSearchTool.SearchCriteria criteria = ShopSearchTool.SearchCriteria.from("适合聚餐", "小寨", "影院", null);

        assertEquals(null, criteria.getTextKeyword());
        assertEquals("小寨", criteria.getArea());
        assertEquals("电影", criteria.getTypeKeyword());
    }

    @Test
    void recognizesCommonTypeAliases() {
        assertEquals("电影", ShopSearchTool.SearchCriteria.canonicalType("电影院"));
        assertEquals("KTV", ShopSearchTool.SearchCriteria.canonicalType("唱歌"));
        assertEquals("美食", ShopSearchTool.SearchCriteria.canonicalType("火锅"));
    }

    @Test
    void extractsAreaFromNaturalLanguageSentence() {
        ShopSearchTool.SearchCriteria criteria = ShopSearchTool.SearchCriteria.from("一家三口想去小寨看电影，能去哪里", null, null, null);

        assertEquals("小寨", criteria.getArea());
        assertEquals("电影", criteria.getTypeKeyword());
    }
}
