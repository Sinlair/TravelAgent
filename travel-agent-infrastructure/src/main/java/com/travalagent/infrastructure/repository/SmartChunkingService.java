package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能分块服务 - 将长文档智能分割为适合 RAG 检索的知识块
 * 
 * 分块策略：
 * 1. 按主题分块 - 景点/酒店/交通/美食/活动
 * 2. 按地理位置分块 - 城市/区域/地标
 * 3. 按时间维度分块 - 季节/月份/时段
 * 4. 语义完整性 - 避免在句子中间切断
 * 5. 重叠分块 - 10-20% overlap 保持上下文连贯
 */
public class SmartChunkingService {

    // 分块配置
    private static final int MIN_CHUNK_SIZE = 200;      // 最小分块大小（字符）
    private static final int MAX_CHUNK_SIZE = 800;      // 最大分块大小（字符）
    private static final int OVERLAP_SIZE = 100;        // 重叠大小（字符）
    private static final double OVERLAP_RATIO = 0.15;   // 重叠比例 15%

    /**
     * 智能分块主方法
     * 
     * @param snippet 原始知识片段
     * @return 分块后的知识片段列表
     */
    public List<TravelKnowledgeSnippet> chunk(TravelKnowledgeSnippet snippet) {
        if (snippet == null || snippet.content() == null || snippet.content().isBlank()) {
            return List.of();
        }

        // 如果内容较短，不需要分块
        if (snippet.content().length() <= MAX_CHUNK_SIZE) {
            return List.of(snippet);
        }

        // 策略1: 尝试按主题分块
        List<TravelKnowledgeSnippet> topicChunks = chunkByTopic(snippet);
        if (!topicChunks.isEmpty() && isValidChunking(topicChunks)) {
            return topicChunks;
        }

        // 策略2: 尝试按地理分块
        List<TravelKnowledgeSnippet> locationChunks = chunkByLocation(snippet);
        if (!locationChunks.isEmpty() && isValidChunking(locationChunks)) {
            return locationChunks;
        }

        // 策略3: 尝试按时间分块
        List<TravelKnowledgeSnippet> timeChunks = chunkByTime(snippet);
        if (!timeChunks.isEmpty() && isValidChunking(timeChunks)) {
            return timeChunks;
        }

        // 策略4: 通用重叠分块
        return chunkWithOverlap(snippet);
    }

    /**
     * 将 Chunk 列表转换为 TravelKnowledgeSnippet 列表
     */
    private List<TravelKnowledgeSnippet> convertChunksToSnippets(TravelKnowledgeSnippet original, List<Chunk> chunks) {
        List<TravelKnowledgeSnippet> snippets = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            String newTitle = original.title() + " - " + chunk.topic + " (" + (i + 1) + "/" + chunks.size() + ")";

            snippets.add(new TravelKnowledgeSnippet(
                    original.city(),
                    original.topic(),
                    newTitle,
                    chunk.content,
                    original.tags(),
                    original.source(),
                    original.schemaSubtype(),
                    original.qualityScore(),
                    original.cityAliases(),
                    original.tripStyleTags(),
                    original.season(),
                    original.budgetLevel(),
                    original.duration(),
                    original.bestTime(),
                    original.crowdLevel(),
                    original.location(),
                    original.area(),
                    original.rating(),
                    original.priceRange(),
                    original.facilities(),
                    original.nearbyPOIs()
            ));
        }
        return snippets;
    }

    /**
     * 按主题分块
     * 识别内容中的不同主题段落（景点/酒店/交通/美食）
     */
    private List<TravelKnowledgeSnippet> chunkByTopic(TravelKnowledgeSnippet snippet) {
        String content = snippet.content();

        // 主题关键词模式
        Map<String, List<String>> topicPatterns = Map.of(
                "景点", List.of("景点", "景区", "必去", "推荐", "打卡", "attraction", "scenic", "must-visit"),
                "酒店", List.of("酒店", "住宿", "住哪里", "客栈", "民宿", "hotel", "accommodation", "stay"),
                "交通", List.of("交通", "地铁", "公交", "机场", "车站", "transit", "transport", "metro"),
                "美食", List.of("美食", "餐厅", "小吃", "推荐菜", "food", "restaurant", "eat", "snack"),
                "购物", List.of("购物", "商场", "步行街", "shopping", "mall", "market"),
                "娱乐", List.of("娱乐", "夜生活", "酒吧", "演出", "entertainment", "nightlife")
        );

        // 查找主题段落
        List<Section> sections = new ArrayList<>();
        String[] paragraphs = content.split("\n\n+");

        for (String paragraph : paragraphs) {
            String normalized = paragraph.toLowerCase();
            String detectedTopic = null;

            for (Map.Entry<String, List<String>> entry : topicPatterns.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (normalized.contains(keyword)) {
                        detectedTopic = entry.getKey();
                        break;
                    }
                }
                if (detectedTopic != null) break;
            }

            if (detectedTopic != null) {
                sections.add(new Section(paragraph, detectedTopic));
            }
        }

        // 如果没有找到明确的主题分段，返回空列表
        if (sections.isEmpty()) {
            return List.of();
        }

        // 合并相邻的相同主题段落
        List<Chunk> mergedChunks = mergeSections(sections);
        return convertChunksToSnippets(snippet, mergedChunks);
    }

    /**
     * 按地理位置分块
     * 识别内容中的不同地理区域
     */
    private List<TravelKnowledgeSnippet> chunkByLocation(TravelKnowledgeSnippet snippet) {
        String content = snippet.content();
        List<Chunk> chunks = new ArrayList<>();

        // 地理标识模式
        Pattern locationPattern = Pattern.compile(
                "(?<area>[\\u4e00-\\u9fa5]{2,10}(?:区|县|市|街道|商圈|景区))" +
                "|(?<landmark>[\\u4e00-\\u9fa5]{2,20}(?:附近|周边|旁边|周围))"
        );

        Matcher matcher = locationPattern.matcher(content);
        List<Integer> splitPositions = new ArrayList<>();

        while (matcher.find()) {
            splitPositions.add(matcher.start());
        }

        // 如果没有找到地理标识，返回空列表
        if (splitPositions.isEmpty()) {
            return List.of();
        }

        // 按位置分块
        int lastPos = 0;
        for (int i = 0; i < splitPositions.size(); i++) {
            int startPos = splitPositions.get(i);
            int endPos = (i + 1 < splitPositions.size()) ? splitPositions.get(i + 1) : content.length();

            // 添加重叠
            if (lastPos > 0) {
                startPos = Math.max(startPos - OVERLAP_SIZE, 0);
            }

            String chunkContent = content.substring(startPos, Math.min(endPos, content.length()));

            if (chunkContent.length() >= MIN_CHUNK_SIZE) {
                // 提取区域名
                String area = extractAreaName(content, startPos);
                chunks.add(new Chunk(area != null ? area : "区域" + (i + 1), chunkContent));
            }

            lastPos = endPos;
        }

        return convertChunksToSnippets(snippet, chunks);
    }

    /**
     * 按时间维度分块
     * 识别内容中的季节性或时间性信息
     */
    private List<TravelKnowledgeSnippet> chunkByTime(TravelKnowledgeSnippet snippet) {
        String content = snippet.content();
        List<TravelKnowledgeSnippet> chunks = new ArrayList<>();

        // 季节模式
        Map<String, List<String>> seasonPatterns = Map.of(
                "春季", List.of("春季", "春天", "3月", "4月", "5月", "spring", "cherry blossom"),
                "夏季", List.of("夏季", "夏天", "6月", "7月", "8月", "summer", "beach"),
                "秋季", List.of("秋季", "秋天", "9月", "10月", "11月", "autumn", "fall", "红叶"),
                "冬季", List.of("冬季", "冬天", "12月", "1月", "2月", "winter", "snow", "滑雪")
        );

        // 查找季节性内容
        for (Map.Entry<String, List<String>> entry : seasonPatterns.entrySet()) {
            String season = entry.getKey();
            List<String> keywords = entry.getValue();

            // 查找包含季节关键词的段落
            String[] paragraphs = content.split("\n\n+");
            StringBuilder seasonContent = new StringBuilder();

            for (String paragraph : paragraphs) {
                String lower = paragraph.toLowerCase();
                for (String keyword : keywords) {
                    if (lower.contains(keyword.toLowerCase())) {
                        if (seasonContent.length() > 0) {
                            seasonContent.append("\n\n");
                        }
                        seasonContent.append(paragraph);
                        break;
                    }
                }
            }

            // 如果找到季节性内容
            if (seasonContent.length() >= MIN_CHUNK_SIZE) {
                List<String> newSeason = new ArrayList<>(snippet.season());
                newSeason.add(season);

                chunks.add(new TravelKnowledgeSnippet(
                        snippet.city(),
                        snippet.topic(),
                        snippet.title() + " - " + season,
                        seasonContent.toString(),
                        snippet.tags(),
                        snippet.source(),
                        snippet.schemaSubtype(),
                        snippet.qualityScore(),
                        snippet.cityAliases(),
                        snippet.tripStyleTags(),
                        List.copyOf(newSeason),  // 更新季节信息
                        snippet.budgetLevel(),
                        snippet.duration(),
                        snippet.bestTime(),
                        snippet.crowdLevel(),
                        snippet.location(),
                        snippet.area(),
                        snippet.rating(),
                        snippet.priceRange(),
                        snippet.facilities(),
                        snippet.nearbyPOIs()
                ));
            }
        }

        return chunks;
    }

    /**
     * 重叠分块（通用策略）
     * 将长文档按固定大小分块，带有重叠以保持上下文
     */
    private List<TravelKnowledgeSnippet> chunkWithOverlap(TravelKnowledgeSnippet snippet) {
        String content = snippet.content();
        List<TravelKnowledgeSnippet> chunks = new ArrayList<>();

        int chunkSize = MAX_CHUNK_SIZE;
        int overlap = (int) (chunkSize * OVERLAP_RATIO);
        int step = chunkSize - overlap;

        int pos = 0;
        int chunkIndex = 0;

        while (pos < content.length()) {
            int endPos = Math.min(pos + chunkSize, content.length());

            // 在句子边界处切分
            if (endPos < content.length()) {
                endPos = findSentenceBoundary(content, endPos);
            }

            String chunkContent = content.substring(pos, endPos);

            if (chunkContent.length() >= MIN_CHUNK_SIZE) {
                chunks.add(new TravelKnowledgeSnippet(
                        snippet.city(),
                        snippet.topic(),
                        snippet.title() + " (第" + (chunkIndex + 1) + "部分)",
                        chunkContent,
                        snippet.tags(),
                        snippet.source(),
                        snippet.schemaSubtype(),
                        snippet.qualityScore(),
                        snippet.cityAliases(),
                        snippet.tripStyleTags(),
                        snippet.season(),
                        snippet.budgetLevel(),
                        snippet.duration(),
                        snippet.bestTime(),
                        snippet.crowdLevel(),
                        snippet.location(),
                        snippet.area(),
                        snippet.rating(),
                        snippet.priceRange(),
                        snippet.facilities(),
                        snippet.nearbyPOIs()
                ));
                chunkIndex++;
            }

            pos += step;
        }

        return chunks.isEmpty() ? List.of(snippet) : chunks;
    }

    /**
     * 查找句子边界（避免在句子中间切断）
     */
    private int findSentenceBoundary(String content, int targetPos) {
        // 向前查找句子结束符
        for (int i = targetPos; i > targetPos - 200 && i >= 0; i--) {
            char c = content.charAt(i);
            if (c == '.' || c == '。' || c == '!' || c == '！' ||
                c == '?' || c == '？' || c == '\n') {
                return i + 1;
            }
        }

        // 如果没找到，向后查找
        for (int i = targetPos; i < targetPos + 100 && i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '.' || c == '。' || c == '!' || c == '！' ||
                c == '?' || c == '？' || c == '\n') {
                return i + 1;
            }
        }

        // 都找不到，返回原位置
        return targetPos;
    }

    /**
     * 合并相邻的相同主题段落
     */
    private List<Chunk> mergeSections(List<Section> sections) {
        if (sections.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        Chunk currentChunk = new Chunk(sections.get(0).topic, sections.get(0).content);

        for (int i = 1; i < sections.size(); i++) {
            Section section = sections.get(i);

            // 如果主题相同且总长度不超过限制，合并
            if (section.topic.equals(currentChunk.topic) &&
                currentChunk.content.length() + section.content.length() < MAX_CHUNK_SIZE) {
                currentChunk.content += "\n\n" + section.content;
            } else {
                // 创建新块
                chunks.add(currentChunk);
                currentChunk = new Chunk(section.topic, section.content);
            }
        }

        // 添加最后一个块
        chunks.add(currentChunk);

        return chunks;
    }

    /**
     * 提取区域名称
     */
    private String extractAreaName(String content, int position) {
        // 向前查找区域名
        int start = Math.max(0, position - 50);
        String context = content.substring(start, position);

        Pattern pattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}(?:区|县|市|街道|商圈|景区))");
        Matcher matcher = pattern.matcher(context);

        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }

        return lastMatch;
    }

    /**
     * 验证分块结果是否有效
     */
    private boolean isValidChunking(List<TravelKnowledgeSnippet> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }

        // 每个块都应该有合理的大小
        for (TravelKnowledgeSnippet chunk : chunks) {
            if (chunk.content().length() < MIN_CHUNK_SIZE / 2) {
                return false;
            }
        }

        // 至少分成2块
        return chunks.size() >= 2;
    }

    /**
     * 内部类：段落
     */
    private static class Section {
        String content;
        String topic;

        Section(String content, String topic) {
            this.content = content;
            this.topic = topic;
        }
    }

    /**
     * 内部类：知识块
     */
    private static class Chunk {
        String topic;
        String content;

        Chunk(String topic, String content) {
            this.topic = topic;
            this.content = content;
        }
    }

    /**
     * 批量分块
     */
    public List<TravelKnowledgeSnippet> chunkAll(List<TravelKnowledgeSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }

        return snippets.stream()
                .map(this::chunk)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 获取分块统计信息
     */
    public Map<String, Object> getChunkingStats(List<TravelKnowledgeSnippet> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Map.of();
        }

        int totalChunks = chunks.size();
        int totalSize = chunks.stream().mapToInt(s -> s.content().length()).sum();
        double avgSize = (double) totalSize / totalChunks;
        int minSize = chunks.stream().mapToInt(s -> s.content().length()).min().orElse(0);
        int maxSize = chunks.stream().mapToInt(s -> s.content().length()).max().orElse(0);

        return Map.of(
                "totalChunks", totalChunks,
                "totalSize", totalSize,
                "avgChunkSize", (int) Math.round(avgSize),
                "minChunkSize", minSize,
                "maxChunkSize", maxSize
        );
    }
}
