package com.travalagent.domain.model.valobj;

import java.util.List;

public record TravelKnowledgeSnippet(
        String city,
        String topic,
        String title,
        String content,
        List<String> tags,
        String source,
        String schemaSubtype,
        Integer qualityScore,
        List<String> cityAliases,
        List<String> tripStyleTags,
        // 增强元数据字段
        List<String> season,              // 适用季节：春/夏/秋/冬
        String budgetLevel,               // 预算等级：free/budget/moderate/premium/luxury
        String duration,                  // 建议时长：1小时/半天/全天/2天
        String bestTime,                  // 最佳时间：早晨/下午/傍晚/夜晚
        String crowdLevel,                // 拥挤度：低/中/高
        String location,                  // 具体位置/地址
        String area,                      // 所在区域
        Double rating,                    // 评分：0-5
        String priceRange,                // 价格范围：¥50-100
        List<String> facilities,          // 设施标签：WiFi/停车场/早餐
        List<String> nearbyPOIs           // 周边兴趣点
) {

    public TravelKnowledgeSnippet {
        tags = tags == null ? List.of() : List.copyOf(tags);
        cityAliases = cityAliases == null ? List.of() : List.copyOf(cityAliases);
        tripStyleTags = tripStyleTags == null ? List.of() : List.copyOf(tripStyleTags);
        season = season == null ? List.of() : List.copyOf(season);
        facilities = facilities == null ? List.of() : List.copyOf(facilities);
        nearbyPOIs = nearbyPOIs == null ? List.of() : List.copyOf(nearbyPOIs);
    }

    public TravelKnowledgeSnippet(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source,
            String schemaSubtype,
            Integer qualityScore,
            List<String> cityAliases,
            List<String> tripStyleTags
    ) {
        this(city, topic, title, content, tags, source, schemaSubtype, qualityScore, cityAliases, tripStyleTags,
             List.of(), null, null, null, null, null, null, null, null, List.of(), List.of());
    }

    // 向后兼容的构造函数（旧的 6 参数版本）
    public TravelKnowledgeSnippet(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source
    ) {
        this(city, topic, title, content, tags, source, null, null, List.of(), List.of(),
             List.of(), null, null, null, null, null, null, null, null, List.of(), List.of());
    }

    // 向后兼容的构造函数（旧的 8 参数版本）
    public TravelKnowledgeSnippet(
            String city,
            String topic,
            String title,
            String content,
            List<String> tags,
            String source,
            String schemaSubtype,
            Integer qualityScore
    ) {
        this(city, topic, title, content, tags, source, schemaSubtype, qualityScore, List.of(), List.of(),
             List.of(), null, null, null, null, null, null, null, null, List.of(), List.of());
    }
}
