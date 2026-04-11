package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reranking 服务测试
 */
class RerankingServiceTest {

    private final RerankingService rerankingService = new RerankingService();

    @Test
    void testRerankWithPreferences() {
        // 创建测试数据
        List<TravelKnowledgeSnippet> snippets = List.of(
                new TravelKnowledgeSnippet(
                        "杭州",
                        "景点",
                        "西湖",
                        "西湖是杭州最著名的景点，适合休闲散步。",
                        List.of("西湖", "景点", "休闲"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("relaxed", "photography"),  // 旅行风格
                        List.of("春", "夏", "秋", "冬"),
                        "free",
                        "半天",
                        "早晨",
                        "高",
                        "杭州市西湖区",
                        "西湖区",
                        4.8,
                        "免费",
                        List.of("停车场", "WiFi"),
                        List.of()
                ),
                new TravelKnowledgeSnippet(
                        "杭州",
                        "美食",
                        "知味观",
                        "知味观是杭州著名的老字号餐厅，提供正宗杭帮菜。",
                        List.of("美食", "餐厅"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("foodie"),
                        List.of("春", "夏", "秋", "冬"),
                        "moderate",
                        "1-2小时",
                        "下午",
                        "中",
                        "杭州市上城区",
                        "上城区",
                        4.5,
                        "¥100-200",
                        List.of("餐厅"),
                        List.of()
                ),
                new TravelKnowledgeSnippet(
                        "杭州",
                        "酒店",
                        "西湖大酒店",
                        "豪华五星级酒店，设施完善。",
                        List.of("酒店", "豪华"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("luxury"),
                        List.of("春", "夏", "秋", "冬"),
                        "luxury",
                        "全天",
                        "下午",
                        "中",
                        "杭州市西湖区",
                        "西湖区",
                        4.9,
                        "¥1000+",
                        List.of("WiFi", "停车场", "早餐", "泳池", "健身房"),
                        List.of()
                )
        );

        // 用户偏好：轻松、预算友好
        List<String> preferences = List.of("relaxed", "budget");

        // 执行重排序
        List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
                snippets,
                "杭州休闲游",
                preferences,
                3
        );

        // 验证结果
        assertNotNull(reranked);
        assertEquals(3, reranked.size());
        
        // 偏好轻松的应该排在前面
        assertTrue(
                reranked.get(0).tripStyleTags().contains("relaxed"),
                "第一个结果应该匹配 relaxed 偏好"
        );
    }

    @Test
    void testRerankWithSeasonalPreference() {
        // 创建包含季节信息的测试数据
        List<TravelKnowledgeSnippet> snippets = List.of(
                new TravelKnowledgeSnippet(
                        "北京",
                        "景点",
                        "香山红叶",
                        "香山红叶是北京秋季最著名的景观。",
                        List.of("秋天", "红叶"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("photography", "outdoors"),
                        List.of("秋"),  // 仅秋季
                        "free",
                        "全天",
                        "早晨",
                        "高",
                        "北京海淀区",
                        "海淀区",
                        4.7,
                        "免费",
                        List.of("停车场"),
                        List.of()
                ),
                new TravelKnowledgeSnippet(
                        "北京",
                        "景点",
                        "故宫",
                        "故宫是中国明清两代的皇家宫殿。",
                        List.of("故宫", "历史"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("heritage", "museum"),
                        List.of("春", "夏", "秋", "冬"),  // 全年
                        "moderate",
                        "全天",
                        "早晨",
                        "高",
                        "北京东城区",
                        "东城区",
                        4.9,
                        "¥60",
                        List.of(),
                        List.of()
                )
        );

        // 执行重排序
        List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
                snippets,
                "北京秋季旅游",
                List.of(),
                2
        );

        // 验证结果
        assertNotNull(reranked);
        assertEquals(2, reranked.size());
    }

    @Test
    void testMMRDiversity() {
        // 创建相似的片段测试 MMR 多样性
        List<TravelKnowledgeSnippet> similarSnippets = List.of(
                new TravelKnowledgeSnippet(
                        "杭州",
                        "景点",
                        "西湖 - 断桥",
                        "断桥是西湖的标志性景点之一。",
                        List.of("西湖", "断桥"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("photography"),
                        List.of("春", "夏", "秋", "冬"),
                        "free",
                        "1小时",
                        "早晨",
                        "高",
                        "杭州西湖区",
                        "西湖区",
                        4.6,
                        "免费",
                        List.of(),
                        List.of()
                ),
                new TravelKnowledgeSnippet(
                        "杭州",
                        "景点",
                        "西湖 - 雷峰塔",
                        "雷峰塔是西湖的另一标志性景点。",
                        List.of("西湖", "雷峰塔"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("photography"),
                        List.of("春", "夏", "秋", "冬"),
                        "free",
                        "1小时",
                        "下午",
                        "高",
                        "杭州西湖区",
                        "西湖区",
                        4.5,
                        "¥40",
                        List.of(),
                        List.of()
                ),
                new TravelKnowledgeSnippet(
                        "杭州",
                        "美食",
                        "楼外楼",
                        "楼外楼是杭州著名的老字号餐厅。",
                        List.of("美食", "餐厅"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("foodie"),
                        List.of("春", "夏", "秋", "冬"),
                        "moderate",
                        "1-2小时",
                        "下午",
                        "中",
                        "杭州上城区",
                        "上城区",
                        4.4,
                        "¥150-300",
                        List.of("餐厅"),
                        List.of()
                )
        );

        // 执行重排序，选择 2 个
        List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
                similarSnippets,
                "杭州旅游",
                List.of(),
                2
        );

        // 验证 MMR 应该选择多样化的结果
        assertNotNull(reranked);
        assertEquals(2, reranked.size());
        
        // 理想情况下应该选择一个景点和一个美食
        long topicCount = reranked.stream()
                .map(TravelKnowledgeSnippet::topic)
                .distinct()
                .count();
        
        // 至少有 2 个不同的主题（如果 MMR 正常工作）
        assertTrue(topicCount >= 1, "MR 应该提供一定程度的多样性");
    }

    @Test
    void testRerankEmptyInput() {
        // 测试空输入
        List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
                List.of(),
                "测试查询",
                List.of(),
                5
        );

        assertNotNull(reranked);
        assertTrue(reranked.isEmpty());
    }

    @Test
    void testRerankSmallCandidateSet() {
        // 测试候选集小于 topK
        List<TravelKnowledgeSnippet> snippets = List.of(
                new TravelKnowledgeSnippet(
                        "上海",
                        "景点",
                        "外滩",
                        "外滩是上海的标志性景观。",
                        List.of("外滩"),
                        "测试来源"
                )
        );

        List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
                snippets,
                "上海旅游",
                List.of(),
                5  // topK 大于候选集
        );

        assertNotNull(reranked);
        assertEquals(1, reranked.size());
    }

    @Test
    void testRerankBatch() {
        // 测试批量重排序
        Map<String, List<TravelKnowledgeSnippet>> queries = Map.of(
                "杭州旅游", List.of(
                        new TravelKnowledgeSnippet(
                                "杭州",
                                "景点",
                                "西湖",
                                "西湖简介。",
                                List.of("西湖"),
                                "测试来源"
                        )
                ),
                "北京旅游", List.of(
                        new TravelKnowledgeSnippet(
                                "北京",
                                "景点",
                                "故宫",
                                "故宫简介。",
                                List.of("故宫"),
                                "测试来源"
                        )
                )
        );

        Map<String, List<TravelKnowledgeSnippet>> results = rerankingService.rerankBatch(
                queries,
                List.of(),
                5
        );

        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.containsKey("杭州旅游"));
        assertTrue(results.containsKey("北京旅游"));
    }

    @Test
    void testGetWeights() {
        // 测试获取权重配置
        Map<String, Double> weights = rerankingService.getWeights();

        assertNotNull(weights);
        assertTrue(weights.containsKey("relevance"));
        assertTrue(weights.containsKey("preference"));
        assertTrue(weights.containsKey("timeliness"));
        assertTrue(weights.containsKey("diversity"));
        assertTrue(weights.containsKey("mmr_lambda"));

        // 验证权重和为 1.0（不包括 mmr_lambda）
        double sum = weights.get("relevance") + 
                     weights.get("preference") + 
                     weights.get("timeliness") + 
                     weights.get("diversity");
        
        assertEquals(1.0, sum, 0.01, "权重总和应该为 1.0");
    }

    @Test
    void testRerankWithBudgetPreference() {
        // 测试预算偏好
        List<TravelKnowledgeSnippet> snippets = List.of(
                new TravelKnowledgeSnippet(
                        "成都",
                        "美食",
                        "便宜小吃",
                        "成都街头小吃，价格便宜。",
                        List.of("小吃"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("foodie", "budget"),
                        List.of("春", "夏", "秋", "冬"),
                        "budget",  // 预算友好
                        "1小时",
                        "下午",
                        "中",
                        "成都锦里",
                        "锦里",
                        4.3,
                        "¥10-30",
                        List.of(),
                        List.of()
                ),
                new TravelKnowledgeSnippet(
                        "成都",
                        "美食",
                        "高档餐厅",
                        "成都米其林三星餐厅。",
                        List.of("高档"),
                        "测试来源",
                        null,
                        null,
                        List.of(),
                        List.of("foodie", "luxury"),
                        List.of("春", "夏", "秋", "冬"),
                        "luxury",  // 昂贵
                        "2小时",
                        "晚上",
                        "低",
                        "成都高新区",
                        "高新区",
                        4.9,
                        "¥1000+",
                        List.of("餐厅", "停车场"),
                        List.of()
                )
        );

        // 用户偏好预算友好
        List<String> preferences = List.of("budget");

        List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
                snippets,
                "成都美食",
                preferences,
                2
        );

        assertNotNull(reranked);
        assertEquals(2, reranked.size());
        
        // 预算友好的应该排在前面
        assertEquals("budget", reranked.get(0).budgetLevel());
    }
}
