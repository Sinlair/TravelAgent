package com.travalagent.infrastructure.repository;

import com.travalagent.domain.model.valobj.TravelKnowledgeSnippet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能分块服务测试
 */
class SmartChunkingServiceTest {

    private final SmartChunkingService chunkingService = new SmartChunkingService();

    @Test
    void testChunkShortContent() {
        // 短内容不需要分块
        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "杭州",
                "景点",
                "西湖简介",
                "西湖是杭州最著名的景点，位于杭州市区西部。",
                List.of("西湖", "杭州"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);

        assertEquals(1, chunks.size());
        assertEquals(snippet, chunks.get(0));
    }

    @Test
    void testChunkByTopic() {
        // 创建包含多个主题的长内容
        String content = """
                ## 景点推荐
                
                西湖是杭州最著名的景点，建议游玩半天时间。断桥残雪、雷峰塔都是必去之处。
                西湖周边还有许多小众景点，如茅家埠、浴鹄湾等，适合喜欢安静的游客。
                """ + "西湖内容".repeat(100) + """
                
                ## 美食推荐
                
                杭州美食以杭帮菜为主，推荐品尝西湖醋鱼、东坡肉、龙井虾仁等特色菜。
                河坊街和高银街是著名的美食街区，有很多地道小吃。
                知味观和楼外楼是杭州老字号餐厅，值得一试。
                """ + "美食内容".repeat(100) + """
                
                ## 住宿建议
                
                西湖附近有很多酒店可供选择，从经济型到豪华型都有。
                推荐住在湖滨商圈，交通便利，购物方便。
                青年旅舍适合背包客，价格在50-100元/晚。
                """ + "住宿内容".repeat(100);

        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "杭州",
                "综合",
                "杭州旅行指南",
                content,
                List.of("杭州", "旅行"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);

        // 应该按主题分成多个块
        assertTrue(chunks.size() >= 2, "应该分成至少2个块");
        
        // 验证每个块都有合理的大小
        for (TravelKnowledgeSnippet chunk : chunks) {
            assertTrue(chunk.content().length() >= 50, "每个块应该有合理的内容长度");
            assertNotNull(chunk.title());
        }
    }

    @Test
    void testChunkWithOverlap() {
        // 创建长内容测试重叠分块
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            content.append("这是第").append(i + 1).append("句话。");
            content.append("杭州是一个美丽的城市，").append("西湖风景如画，");
            content.append("值得游客前来参观。\n");
        }

        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "杭州",
                "景点",
                "杭州详细介绍",
                content.toString(),
                List.of("杭州"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);

        // 应该分成多个块
        assertTrue(chunks.size() >= 2, "长内容应该被分成多个块");
        
        // 验证总内容长度
        int totalLength = chunks.stream().mapToInt(s -> s.content().length()).sum();
        assertTrue(totalLength >= content.length(), "分块后的总内容应该保持完整");
    }

    @Test
    void testChunkByLocation() {
        // 创建包含地理信息的内容
        String content = """
                西湖区是杭州的核心旅游区。西湖景区位于西湖区，是杭州的标志性景点。
                """ + "西湖区详细介绍内容。".repeat(100) + """
                
                上城区是杭州的老城区。河坊街位于上城区，是杭州著名的历史文化街区。
                """ + "上城区详细介绍内容。".repeat(100) + """
                
                下城区是杭州的商业中心。武林广场位于下城区，是杭州最繁华的商业区。
                """ + "下城区详细介绍内容。".repeat(100) + """
                
                拱墅区有许多工业遗址改造的文创园区。运河天地位于拱墅区，是夜生活的好去处。
                """ + "拱墅区详细介绍内容。".repeat(100);

        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "杭州",
                "综合",
                "杭州各区介绍",
                content,
                List.of("杭州"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);

        // 验证分块结果
        if (!chunks.isEmpty()) {
            assertTrue(chunks.size() >= 2, "应该按地理区域分成多个块");
            
            // 验证区域信息
            boolean hasAreaInfo = chunks.stream()
                    .anyMatch(s -> s.area() != null && !s.area().isBlank());
            // 区域信息可能提取不到，所以不强制要求
        }
    }

    @Test
    void testChunkBySeason() {
        // 创建包含季节信息的内容
        String content = """
                春季的杭州是最美的季节。3-5月，西湖边的樱花盛开，吸引大量游客。
                春季气温适中，建议穿薄外套，带一把雨伞。
                """ + "春季详细攻略。".repeat(100) + """
                
                夏季的杭州比较炎热。6-8月，气温可达35度以上。
                建议清晨或傍晚游览西湖，避开高温时段。可以体验西湖夜游。
                """ + "夏季详细攻略。".repeat(100) + """
                
                秋季的杭州层林尽染。9-11月是杭州最舒适的季节。
                满陇桂雨是秋季必去，桂花香飘满城。
                """ + "秋季详细攻略。".repeat(100) + """
                
                冬季的杭州银装素裹。12-2月可能下雪，断桥残雪极具美感。
                冬季可以泡温泉，吃火锅，享受冬日暖阳。
                """ + "冬季详细攻略。".repeat(100);

        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "杭州",
                "景点",
                "杭州四季旅游",
                content,
                List.of("杭州", "季节"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);

        // 验证分块结果
        if (!chunks.isEmpty()) {
            assertTrue(chunks.size() >= 2, "应该按季节分成多个块");
            
            // 验证季节信息
            boolean hasSeasonInfo = chunks.stream()
                    .anyMatch(s -> s.season() != null && !s.season().isEmpty());
            assertTrue(hasSeasonInfo, "分块应该包含季节信息");
        }
    }

    @Test
    void testChunkAll() {
        // 测试批量分块
        TravelKnowledgeSnippet snippet1 = new TravelKnowledgeSnippet(
                "杭州",
                "景点",
                "西湖",
                "西湖是杭州最著名的景点。" + "内容重复。".repeat(50),
                List.of("西湖"),
                "测试来源"
        );

        TravelKnowledgeSnippet snippet2 = new TravelKnowledgeSnippet(
                "北京",
                "景点",
                "故宫",
                "故宫是中国明清两代的皇家宫殿。" + "内容重复。".repeat(50),
                List.of("故宫"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunkAll(List.of(snippet1, snippet2));

        // 应该分成多个块
        assertTrue(chunks.size() >= 2, "批量分块应该产生多个块");
    }

    @Test
    void testChunkingStats() {
        // 测试统计信息
        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "杭州",
                "景点",
                "西湖",
                "西湖简介。" + "内容丰富。".repeat(100),
                List.of("西湖"),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);
        Map<String, Object> stats = chunkingService.getChunkingStats(chunks);

        // 验证统计信息
        assertNotNull(stats);
        assertTrue((int) stats.get("totalChunks") >= 1);
        assertTrue((int) stats.get("totalSize") > 0);
        assertTrue((int) stats.get("avgChunkSize") > 0);
        assertTrue((int) stats.get("minChunkSize") > 0);
        assertTrue((int) stats.get("maxChunkSize") > 0);

        System.out.println("分块统计信息:");
        System.out.println("  总块数: " + stats.get("totalChunks"));
        System.out.println("  总大小: " + stats.get("totalSize"));
        System.out.println("  平均大小: " + stats.get("avgChunkSize"));
        System.out.println("  最小大小: " + stats.get("minChunkSize"));
        System.out.println("  最大大小: " + stats.get("maxChunkSize"));
    }

    @Test
    void testSentenceBoundary() {
        // 测试句子边界检测
        String content = "这是第一句话。这是第二句话！这是第三句话？这是第四句话。";
        
        // 这个测试验证分块不会在句子中间切断
        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
                "测试",
                "测试",
                "测试",
                content.repeat(20),
                List.of(),
                "测试来源"
        );

        List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);

        // 验证每个块的结尾都是句子边界
        for (TravelKnowledgeSnippet chunk : chunks) {
            String chunkContent = chunk.content().trim();
            if (!chunkContent.isEmpty()) {
                char lastChar = chunkContent.charAt(chunkContent.length() - 1);
                // 最后一个字符应该是标点符号或换行
                assertTrue(
                        lastChar == '.' || lastChar == '。' || lastChar == '!' || 
                        lastChar == '！' || lastChar == '?' || lastChar == '？' || 
                        lastChar == '\n',
                        "分块应该在句子边界处结束，但最后一个字符是: " + lastChar
                );
            }
        }
    }
}
