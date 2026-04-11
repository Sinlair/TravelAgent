package com.travalagent.infrastructure.repository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 查询理解服务 - 增强和优化用户查询
 * 
 * 功能：
 * 1. 查询扩展 - 同义词、相关词
 * 2. 查询重写 - 标准化地名、时间
 * 3. 意图识别 - 找景点/酒店/美食/交通
 * 4. 上下文保持 - 多轮对话
 * 5. 拼写纠错 - 常见错误纠正
 */
public class QueryUnderstandingService {

    // 同义词词典
    private static final Map<String, List<String>> SYNONYMS = new HashMap<>();
    
    // 地名标准化映射
    private static final Map<String, String> CITY_NORMALIZATION = new HashMap<>();
    
    // 意图关键词
    private static final Map<String, List<String>> INTENT_KEYWORDS = new HashMap<>();
    
    // 常见拼写错误
    private static final Map<String, String> SPELLING_CORRECTIONS = new HashMap<>();
    
    static {
        // 初始化同义词
        SYNONYMS.put("景点", List.of("景区", "旅游区", "attraction", "sight", "scenic spot"));
        SYNONYMS.put("酒店", List.of("宾馆", "旅馆", "住宿", "hotel", "hostel", "民宿"));
        SYNONYMS.put("美食", List.of("餐厅", "小吃", "food", "restaurant", "eat", "餐饮"));
        SYNONYMS.put("交通", List.of("transit", "transport", "metro", "subway", "地铁", "公交"));
        SYNONYMS.put("购物", List.of("shopping", "mall", "market", "商场", "步行街"));
        SYNONYMS.put("好玩", List.of("有趣", "推荐", "必去", "worth", "interesting", "fun"));
        SYNONYMS.put("便宜", List.of("实惠", "经济", "budget", "cheap", "affordable"));
        
        // 地名标准化 (使用 LinkedHashMap 保持顺序)
        Map<String, String> rawCities = new HashMap<>();
        rawCities.put("杭州", "hangzhou");
        rawCities.put("杭州市", "hangzhou");
        rawCities.put("hz", "hangzhou");
        rawCities.put("北京", "beijing");
        rawCities.put("北京市", "beijing");
        rawCities.put("bj", "beijing");
        rawCities.put("上海", "shanghai");
        rawCities.put("上海市", "shanghai");
        rawCities.put("sh", "shanghai");
        rawCities.put("成都", "chengdu");
        rawCities.put("成都市", "chengdu");
        rawCities.put("cd", "chengdu");
        rawCities.put("广州", "guangzhou");
        rawCities.put("广州市", "guangzhou");
        rawCities.put("gz", "guangzhou");
        rawCities.put("深圳", "shenzhen");
        rawCities.put("深圳市", "shenzhen");
        rawCities.put("sz", "shenzhen");

        // 按长度降序排列，避免 "广州" 匹配到 "广州市" 的一部分，或者 "gz" 匹配到 "hangzhou" 中的 "gz"
        rawCities.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()))
                .forEach(e -> CITY_NORMALIZATION.put(e.getKey(), e.getValue()));
        
        // 意图识别关键词
        INTENT_KEYWORDS.put("scenic", List.of("景点", "景区", "玩", "看", "游览", "attraction", "sight", "scenic"));
        INTENT_KEYWORDS.put("hotel", List.of("酒店", "住", "住宿", "hotel", "stay", "accommodation"));
        INTENT_KEYWORDS.put("food", List.of("美食", "吃", "餐厅", "food", "eat", "restaurant"));
        INTENT_KEYWORDS.put("transit", List.of("交通", "怎么去", "transit", "transport", "metro"));
        INTENT_KEYWORDS.put("shopping", List.of("购物", "买", "shopping", "mall", "market"));
        INTENT_KEYWORDS.put("itinerary", List.of("行程", "路线", "规划", "itinerary", "plan", "route"));
        
        // 常见拼写错误
        SPELLING_CORRECTIONS.put("西湖", "西湖");
        SPELLING_CORRECTIONS.put("西糊", "西湖");
        SPELLING_CORRECTIONS.put("故官", "故宫");
        SPELLING_CORRECTIONS.put("故宮", "故宫");
        SPELLING_CORRECTIONS.put("外滩", "外滩");
        SPELLING_CORRECTIONS.put("外 tan", "外滩");
    }

    /**
     * 查询理解主方法
     * 
     * @param query 原始查询
     * @param conversationHistory 对话历史
     * @return 理解后的查询
     */
    public UnderstoodQuery understand(String query, List<String> conversationHistory) {
        if (query == null || query.isBlank()) {
            return new UnderstoodQuery("", "", List.of(), List.of(), Map.of());
        }

        // 步骤1: 拼写纠错
        String correctedQuery = correctSpelling(query);
        
        // 步骤2: 查询重写（标准化）
        String rewrittenQuery = rewriteQuery(correctedQuery);
        
        // 步骤3: 查询扩展
        List<String> expandedQueries = expandQuery(rewrittenQuery);
        
        // 步骤4: 意图识别
        Map<String, Double> intents = recognizeIntents(rewrittenQuery);
        
        // 步骤5: 上下文融合
        String finalQuery = mergeWithContext(rewrittenQuery, conversationHistory);
        
        // 步骤6: 提取实体
        // 使用 finalQuery 提取，但需要注意城市可能已被标准化
        Map<String, String> entities = extractEntities(finalQuery, correctedQuery);
        
        return new UnderstoodQuery(
                query,
                finalQuery,
                expandedQueries,
                new ArrayList<>(intents.keySet()),
                entities
        );
    }

    /**
     * 拼写纠错
     */
    private String correctSpelling(String query) {
        String corrected = query;
        
        for (Map.Entry<String, String> entry : SPELLING_CORRECTIONS.entrySet()) {
            corrected = corrected.replace(entry.getKey(), entry.getValue());
        }
        
        // 检测并纠正常见的拼音错误
        corrected = correctPinyinErrors(corrected);
        
        return corrected;
    }

    /**
     * 纠正拼音错误
     */
    private String correctPinyinErrors(String query) {
        // 简单的拼音-汉字映射纠错
        Map<String, String> pinyinMap = Map.of(
                "xihu", "西湖",
                "gugong", "故宫",
                "waitan", "外滩",
                "tiananmen", "天安门",
                "changcheng", "长城"
        );
        
        String corrected = query;
        for (Map.Entry<String, String> entry : pinyinMap.entrySet()) {
            if (corrected.toLowerCase().contains(entry.getKey())) {
                corrected = corrected.replaceAll("(?i)" + entry.getKey(), entry.getValue());
            }
        }
        
        return corrected;
    }

    /**
     * 查询重写（标准化）
     */
    private String rewriteQuery(String query) {
        String rewritten = query;
        
        // 1. 标准化地名
        rewritten = normalizeCities(rewritten);
        
        // 2. 标准化时间表达
        rewritten = normalizeTimeExpressions(rewritten);
        
        // 3. 标准化预算表达
        rewritten = normalizeBudgetExpressions(rewritten);
        
        // 4. 移除冗余词
        rewritten = removeRedundantWords(rewritten);
        
        return rewritten.trim();
    }

    /**
     * 标准化地名
     */
    private String normalizeCities(String query) {
        String normalized = query;
        
        for (Map.Entry<String, String> entry : CITY_NORMALIZATION.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                normalized = normalized.replace(entry.getKey(), entry.getValue());
                // 这里不应该 break，因为可能包含多个地名
            }
        }
        
        return normalized;
    }

    /**
     * 标准化时间表达
     */
    private String normalizeTimeExpressions(String query) {
        String normalized = query;
        
        Map<String, String> timeMap = Map.of(
                "明后天", "future",
                "最近", "recent",
                "周末", "weekend",
                "下周", "next_week",
                "下个月", "next_month"
        );
        
        for (Map.Entry<String, String> entry : timeMap.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                normalized = normalized.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return normalized;
    }

    /**
     * 标准化预算表达
     */
    private String normalizeBudgetExpressions(String query) {
        String normalized = query;
        
        Map<String, String> budgetMap = Map.of(
                "省钱", "budget",
                "穷游", "budget",
                "豪华", "luxury",
                "高档", "luxury",
                "性价比", "moderate"
        );
        
        for (Map.Entry<String, String> entry : budgetMap.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                normalized = normalized.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return normalized;
    }

    /**
     * 移除冗余词
     */
    private String removeRedundantWords(String query) {
        List<String> stopWords = List.of("请问", "请告诉", "我想知道", "能不能", "怎么样", "如何");
        String rewritten = query;
        for (String word : stopWords) {
            rewritten = rewritten.replace(word, "");
        }
        return rewritten;
    }

    /**
     * 查询扩展
     */
    private List<String> expandQuery(String query) {
        List<String> expanded = new ArrayList<>();
        expanded.add(query);  // 保留原始查询
        
        String normalized = query.toLowerCase();
        
        // 1. 添加同义词
        for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                for (String synonym : entry.getValue()) {
                    String expandedQuery = normalized.replace(entry.getKey(), synonym);
                    if (!expanded.contains(expandedQuery)) {
                        expanded.add(expandedQuery);
                    }
                }
            }
        }
        
        // 2. 添加相关词
        List<String> relatedTerms = generateRelatedTerms(query);
        expanded.addAll(relatedTerms);
        
        // 限制扩展数量
        return expanded.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * 生成相关词
     */
    private List<String> generateRelatedTerms(String query) {
        List<String> related = new ArrayList<>();
        String normalized = query.toLowerCase();
        
        // 如果包含城市，添加该城市的热门景点
        for (Map.Entry<String, String> entry : CITY_NORMALIZATION.entrySet()) {
            String city = entry.getKey();
            String normalizedCity = entry.getValue();
            if (normalized.contains(city.toLowerCase()) || normalized.contains(normalizedCity.toLowerCase())) {
                related.add(normalizedCity + " 必去景点");
                related.add(normalizedCity + " 美食推荐");
                related.add(normalizedCity + " 住宿建议");
                related.add(normalizedCity + " 交通攻略");
                break;
            }
        }
        
        // 如果包含景点，添加相关信息
        if (normalized.contains("景点") || normalized.contains("attraction")) {
            related.add("ticket price");  // 门票价格
            related.add("opening hours");  // 开放时间
            related.add("best time to visit");  // 最佳时间
        }
        
        return related;
    }

    /**
     * 意图识别
     */
    private Map<String, Double> recognizeIntents(String query) {
        Map<String, Double> intents = new LinkedHashMap<>();
        String normalized = query.toLowerCase();
        
        for (Map.Entry<String, List<String>> entry : INTENT_KEYWORDS.entrySet()) {
            String intent = entry.getKey();
            List<String> keywords = entry.getValue();
            
            int matchCount = 0;
            for (String keyword : keywords) {
                if (normalized.contains(keyword.toLowerCase())) {
                    matchCount++;
                }
            }
            
            if (matchCount > 0) {
                double confidence = (double) matchCount / keywords.size();
                intents.put(intent, confidence);
            }
        }
        
        // 如果没有识别到意图，默认为景点
        if (intents.isEmpty()) {
            intents.put("scenic", 0.5);
        }
        
        // 按置信度排序
        return intents.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 上下文融合
     */
    private String mergeWithContext(String query, List<String> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return query;
        }
        
        String recentContext = conversationHistory.get(conversationHistory.size() - 1);
        String resolved = query;
        
        // 替换代词
        if (resolved.contains("这个") || resolved.contains("这里") || resolved.contains("那里")) {
            // 从上下文中提取地点
            String location = extractLocationFromContext(conversationHistory);
            if (location != null) {
                resolved = resolved.replace("这个", location);
                resolved = resolved.replace("这里", location);
                resolved = resolved.replace("那里", location);
            }
        }
        
        if (resolved.contains("它") || resolved.contains("其")) {
            String entity = extractEntityFromContext(recentContext);
            if (entity != null) {
                resolved = resolved.replace("它", entity);
                resolved = resolved.replace("其", entity);
            }
        }
        
        // 补充缺失信息
        resolved = fillMissingInfo(resolved, conversationHistory);
        
        return resolved;
    }

    /**
     * 从上下文提取地点
     */
    private String extractLocationFromContext(List<String> conversationHistory) {
        // 从最近的对话开始找
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            String context = conversationHistory.get(i);
            
            // 1. 查找已知的城市
            for (String city : CITY_NORMALIZATION.keySet()) {
                if (context.contains(city)) {
                    return city;
                }
            }
            
            // 2. 查找常见的景点名词
            String[] commonLandmarks = {"西湖", "故宫", "外滩", "兵马俑", "大熊猫基地"};
            for (String landmark : commonLandmarks) {
                if (context.contains(landmark)) {
                    return landmark;
                }
            }
        }
        return null;
    }

    /**
     * 从上下文提取实体
     */
    private String extractEntityFromContext(String context) {
        // 简单实现：返回第一个名词
        String[] words = context.split("\\s+|[，。！？]");
        for (String word : words) {
            if (word.length() > 1 && !word.matches("[\\u4e00-\\u9fa5]{1}")) {
                return word;
            }
        }
        return null;
    }

    /**
     * 补充缺失信息
     */
    private String fillMissingInfo(String query, List<String> conversationHistory) {
        String filled = query;
        
        // 如果查询中没有城市，但从上下文中可以推断
        boolean hasCity = CITY_NORMALIZATION.keySet().stream()
                .anyMatch(filled::contains) || 
                CITY_NORMALIZATION.values().stream().anyMatch(filled::contains);
        
        if (!hasCity && !conversationHistory.isEmpty()) {
            for (int i = conversationHistory.size() - 1; i >= 0; i--) {
                String recentContext = conversationHistory.get(i);
                for (String city : CITY_NORMALIZATION.keySet()) {
                    if (recentContext.contains(city)) {
                        filled = city + " " + filled;
                        return filled;
                    }
                }
            }
        }
        
        return filled;
    }

    /**
     * 提取实体
     */
    private Map<String, String> extractEntities(String finalQuery, String originalQuery) {
        Map<String, String> entities = new LinkedHashMap<>();
        
        // 1. 提取城市 (从原始查询和最终查询中查找)
        for (String city : CITY_NORMALIZATION.keySet()) {
            boolean matched = false;
            // 对于中文字符，直接包含匹配
            if (city.matches("[\\u4e00-\\u9fa5]+")) {
                if (originalQuery.contains(city) || finalQuery.contains(city)) {
                    matched = true;
                }
            } else {
                // 对于英文代码 (如 hz, gz)，使用单词边界匹配，避免匹配到 hangzhou 中的 gz
                Pattern p = Pattern.compile("\\b" + city + "\\b", Pattern.CASE_INSENSITIVE);
                if (p.matcher(originalQuery).find() || p.matcher(finalQuery).find()) {
                    matched = true;
                }
            }

            if (matched) {
                entities.put("city", city);
                entities.put("city_normalized", CITY_NORMALIZATION.get(city));
                break;
            }
        }
        
        // 如果还没找到，尝试从最终查询中找标准化的地名
        if (!entities.containsKey("city")) {
            for (Map.Entry<String, String> entry : CITY_NORMALIZATION.entrySet()) {
                String normalizedValue = entry.getValue();
                // 标准化后的地名通常是英文单词，使用单词边界
                Pattern p = Pattern.compile("\\b" + normalizedValue + "\\b", Pattern.CASE_INSENSITIVE);
                if (p.matcher(finalQuery).find()) {
                    entities.put("city", entry.getKey());
                    entities.put("city_normalized", normalizedValue);
                    break;
                }
            }
        }
        
        // 2. 提取时间
        Pattern timePattern = Pattern.compile("(\\d+月|[春夏秋冬]季?|春天|夏天|秋天|冬天)");
        Matcher timeMatcher = timePattern.matcher(originalQuery);
        if (timeMatcher.find()) {
            entities.put("time", timeMatcher.group(1));
        } else {
            timeMatcher = timePattern.matcher(finalQuery);
            if (timeMatcher.find()) {
                entities.put("time", timeMatcher.group(1));
            }
        }
        
        // 3. 提取预算
        Pattern budgetPattern = Pattern.compile("(budget|luxury|moderate|free|便宜|贵|高端)");
        Matcher budgetMatcher = budgetPattern.matcher(finalQuery);
        if (budgetMatcher.find()) {
            entities.put("budget", budgetMatcher.group(1));
        }
        
        // 4. 提取旅行风格
        Pattern stylePattern = Pattern.compile("(relaxed|family|foodie|budget|轻松|亲子|美食|穷游)");
        Matcher styleMatcher = stylePattern.matcher(finalQuery);
        if (styleMatcher.find()) {
            entities.put("style", styleMatcher.group(1));
        }
        
        return entities;
    }

    /**
     * 批量理解查询
     */
    public List<UnderstoodQuery> understandBatch(List<String> queries, List<String> conversationHistory) {
        return queries.stream()
                .map(q -> understand(q, conversationHistory))
                .collect(Collectors.toList());
    }

    /**
     * 理解后的查询结果
     */
    public record UnderstoodQuery(
            String originalQuery,           // 原始查询
            String rewrittenQuery,          // 重写后的查询
            List<String> expandedQueries,   // 扩展查询
            List<String> intents,           // 识别的意图
            Map<String, String> entities    // 提取的实体
    ) {
    }

    /**
     * 获取同义词词典大小
     */
    public int getSynonymDictionarySize() {
        return SYNONYMS.size();
    }

    /**
     * 获取意图类型数量
     */
    public int getIntentCount() {
        return INTENT_KEYWORDS.size();
    }
}
