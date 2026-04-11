package com.travalagent.infrastructure.repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询理解服务测试
 */
class QueryUnderstandingServiceTest {

    private final QueryUnderstandingService queryService = new QueryUnderstandingService();

    @Test
    void testBasicQueryUnderstanding() {
        // 测试基本查询理解
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "我想去杭州西湖玩",
                List.of()
        );

        assertNotNull(understood);
        assertEquals("我想去杭州西湖玩", understood.originalQuery());
        assertNotNull(understood.rewrittenQuery());
        assertFalse(understood.expandedQueries().isEmpty());
        assertFalse(understood.intents().isEmpty());
    }

    @Test
    void testSpellingCorrection() {
        // 测试拼写纠错
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "我想去西糊玩",
                List.of()
        );

        assertNotNull(understood);
        // 应该纠正为西湖
        assertTrue(
                understood.rewrittenQuery().contains("西湖") || 
                understood.expandedQueries().stream().anyMatch(q -> q.contains("西湖")),
                "应该纠正拼写错误"
        );
    }

    @Test
    void testQueryExpansion() {
        // 测试查询扩展
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "杭州美食推荐",
                List.of()
        );

        assertNotNull(understood);
        assertTrue(understood.expandedQueries().size() > 1, "应该有扩展查询");
        
        // 验证扩展查询包含同义词
        boolean hasExpansion = understood.expandedQueries().stream()
                .anyMatch(q -> q.contains("food") || q.contains("restaurant") || q.contains("餐厅"));
        assertTrue(hasExpansion, "应该包含同义词扩展");
    }

    @Test
    void testIntentRecognition() {
        // 测试意图识别
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "杭州有哪些好玩的景点",
                List.of()
        );

        assertNotNull(understood);
        assertFalse(understood.intents().isEmpty(), "应该识别到意图");
        
        // 应该识别为景点意图
        assertTrue(
                understood.intents().contains("scenic"),
                "应该识别为景点意图"
        );
    }

    @Test
    void testIntentRecognitionForHotel() {
        // 测试酒店意图识别
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "杭州西湖附近有什么好酒店",
                List.of()
        );

        assertNotNull(understood);
        assertTrue(
                understood.intents().contains("hotel"),
                "应该识别为酒店意图"
        );
    }

    @Test
    void testIntentRecognitionForFood() {
        // 测试美食意图识别
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "成都哪里有好吃的小吃",
                List.of()
        );

        assertNotNull(understood);
        assertTrue(
                understood.intents().contains("food"),
                "应该识别为美食意图"
        );
    }

    @Test
    void testEntityExtraction() {
        // 测试实体提取
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "杭州春天有什么好玩的",
                List.of()
        );

        assertNotNull(understood);
        Map<String, String> entities = understood.entities();
        
        // 应该提取到城市
        assertTrue(
                entities.containsKey("city"),
                "应该提取到城市实体"
        );
        assertEquals("杭州", entities.get("city"));
        
        // 应该提取到时间
        assertTrue(
                entities.containsKey("time"),
                "应该提取到时间实体"
        );
    }

    @Test
    void testContextMerge() {
        // 测试上下文融合
        List<String> conversationHistory = List.of(
                "我想去杭州旅游",
                "西湖怎么样？"
        );

        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "这个景点附近有什么好吃的",
                conversationHistory
        );

        assertNotNull(understood);
        // 应该从上下文中补充信息
        assertTrue(
                understood.rewrittenQuery().contains("西湖") || 
                understood.rewrittenQuery().contains("杭州"),
                "应该从上下文中补充地点信息"
        );
    }

    @Test
    void testTimeNormalization() {
        // 测试时间标准化
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "北京春天有什么景点",
                List.of()
        );

        assertNotNull(understood);
        // 应该标准化时间表达
        assertTrue(
                understood.rewrittenQuery().contains("春"),
                "应该标准化季节表达"
        );
    }

    @Test
    void testBudgetNormalization() {
        // 测试预算标准化
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "上海有什么便宜好吃的",
                List.of()
        );

        assertNotNull(understood);
        // 应该标准化预算表达
        assertTrue(
                understood.rewrittenQuery().contains("budget") ||
                understood.expandedQueries().stream().anyMatch(q -> q.contains("budget")),
                "应该标准化预算表达"
        );
    }

    @Test
    void testPronounResolution() {
        // 测试代词解析
        List<String> conversationHistory = List.of(
                "推荐一下杭州的景点",
                "西湖非常不错"
        );

        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "那里附近有什么酒店",
                conversationHistory
        );

        assertNotNull(understood);
        // 应该解析代词
        assertTrue(
                understood.rewrittenQuery().contains("西湖") ||
                understood.rewrittenQuery().contains("杭州"),
                "应该解析代词'那里'"
        );
    }

    @Test
    void testCityNormalization() {
        // 测试城市标准化
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "hz有什么好玩的",
                List.of()
        );

        assertNotNull(understood);
        // 应该识别 hz 为杭州
        Map<String, String> entities = understood.entities();
        assertTrue(
                entities.containsKey("city_normalized"),
                "应该标准化城市名"
        );
    }

    @Test
    void testMultipleIntents() {
        // 测试多意图识别
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "北京三日游行程规划，包括景点和美食",
                List.of()
        );

        assertNotNull(understood);
        // 应该识别到多个意图
        assertTrue(
                understood.intents().size() >= 2,
                "应该识别到多个意图"
        );
    }

    @Test
    void testUnderstandBatch() {
        // 测试批量理解
        List<String> queries = List.of(
                "杭州西湖",
                "北京故宫",
                "上海外滩"
        );

        List<QueryUnderstandingService.UnderstoodQuery> results = queryService.understandBatch(
                queries,
                List.of()
        );

        assertNotNull(results);
        assertEquals(3, results.size());
        
        // 验证每个查询都被理解
        for (QueryUnderstandingService.UnderstoodQuery understood : results) {
            assertNotNull(understood);
            assertNotNull(understood.rewrittenQuery());
        }
    }

    @Test
    void testEmptyQuery() {
        // 测试空查询
        QueryUnderstandingService.UnderstoodQuery understood = queryService.understand(
                "",
                List.of()
        );

        assertNotNull(understood);
        assertEquals("", understood.originalQuery());
        assertEquals("", understood.rewrittenQuery());
    }

    @Test
    void testDictionarySize() {
        // 测试词典大小
        int synonymSize = queryService.getSynonymDictionarySize();
        int intentCount = queryService.getIntentCount();
        
        assertTrue(synonymSize > 0, "同义词词典应该有内容");
        assertTrue(intentCount > 0, "意图词典应该有内容");
    }
}
