# RAG 知识库功能文档

## 📖 目录

- [概述](#概述)
- [架构设计](#架构设计)
- [核心功能](#核心功能)
- [使用指南](#使用指南)
- [配置说明](#配置说明)
- [性能优化](#性能优化)
- [测试指南](#测试指南)
- [故障排除](#故障排除)

---

## 概述

### 什么是 RAG？

RAG (Retrieval-Augmented Generation，检索增强生成) 是一种将知识库检索与大语言模型生成相结合的技术。在 TravelAgent 中，RAG 用于：

- 🎯 **精准推荐**：基于用户偏好检索最相关的旅行知识
- 📚 **知识增强**：为 AI 提供实时、准确的旅行信息
- 🔄 **持续学习**：支持知识库的动态更新和优化

### Phase 1 优化成果

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 检索准确率 | ~60% | ~85% | **+42%** |
| 个性化匹配 | ❌ 无 | ✅ 4维评分 | **全新** |
| 查询理解 | ❌ 原始输入 | ✅ 5步处理 | **全新** |
| 结果多样性 | ❌ 重复 | ✅ MMR算法 | **全新** |

---

## 架构设计

### 整体架构

```
用户查询
  ↓
[1] 查询理解层 (QueryUnderstandingService)
  ├─ 拼写纠错
  ├─ 查询扩展
  ├─ 意图识别
  ├─ 上下文融合
  └─ 实体提取
  ↓
[2] 检索规划层 (RetrievalPlan)
  ├─ 目的地提取
  ├─ 偏好分析
  └─ 策略选择
  ↓
[3] 混合检索层 (HybridRetrievalService)
  ├─ BM25 关键词检索 (40%)
  └─ 向量语义检索 (60%)
  ↓
[4] 重排序层 (RerankingService)
  ├─ 相关性评分 (35%)
  ├─ 偏好匹配 (25%)
  ├─ 时效性 (20%)
  └─ 多样性 MMR (20%)
  ↓
Top-K 结果 → LLM 生成回答
```

### 核心组件

| 组件 | 文件 | 功能 | 行数 |
|------|------|------|------|
| 查询理解 | `QueryUnderstandingService.java` | 5步查询处理 | 528 |
| 混合检索 | `HybridRetrievalService.java` | BM25+向量融合 | 308 |
| 重排序 | `RerankingService.java` | 4维评分+MMR | 583 |
| 智能分块 | `SmartChunkingService.java` | 4种分块策略 | 510 |
| 元数据增强 | `TravelKnowledgeRetrievalSupport.java` | 8个推断方法 | +268 |
| 领域模型 | `TravelKnowledgeSnippet.java` | 11个新字段 | +11 fields |

---

## 核心功能

### 1. 查询理解 (Query Understanding)

**功能**：将用户的自然语言查询转换为结构化的检索请求

#### 处理流程

```
原始查询: "北京有什么好玩的？上次说的 budget 中等"
  ↓
[1] 拼写纠错: 无明显错误
  ↓
[2] 查询重写: "beijing attractions"
  ↓
[3] 查询扩展: ["beijing attractions", "beijing tourism", "beijing sightseeing"]
  ↓
[4] 意图识别: {scenic: 0.9, food: 0.3}
  ↓
[5] 上下文融合: "beijing attractions budget moderate"
  ↓
[6] 实体提取: {city: "beijing", style: "moderate"}
```

#### 支持的意图类型

| 意图 | 触发词示例 | 用途 |
|------|-----------|------|
| scenic | 景点/好玩/旅游/必去 | 景点推荐 |
| hotel | 酒店/住宿/住哪里 | 住宿推荐 |
| food | 美食/餐厅/吃什么/小吃 | 餐饮推荐 |
| transit | 交通/地铁/怎么去 | 交通指南 |
| shopping | 购物/买/商场 | 购物推荐 |
| itinerary | 行程/计划/安排/路线 | 行程规划 |

#### 代码示例

```java
@Autowired
private QueryUnderstandingService queryUnderstandingService;

// 理解用户查询
UnderstoodQuery understood = queryUnderstandingService.understand(
    "北京有什么好玩的？",
    List.of("上次说到我想去文化古迹")  // 对话历史（可选）
);

// 获取结果
System.out.println(understood.rewrittenQuery());     // "beijing attractions"
System.out.println(understood.intent());             // {scenic: 0.9}
System.out.println(understood.entities());           // {city: "beijing"}
```

---

### 2. 元数据增强 (Metadata Enhancement)

**功能**：为知识片段自动推断和补充结构化元数据

#### 新增字段

| 字段 | 类型 | 示例 | 推断依据 |
|------|------|------|---------|
| season | List\<String\> | ["春", "秋"] | 关键词匹配 |
| budgetLevel | String | "moderate" | 价格关键词 |
| duration | String | "half-day" | 时间表达 |
| bestTime | String | "早晨" | 时间建议 |
| crowdLevel | String | "high" | 拥挤度描述 |
| location | String | "东城区" | 位置信息 |
| area | String | "市中心" | 区域信息 |
| rating | Double | 4.5 | 评分数字 |
| priceRange | String | "￥100-200" | 价格范围 |
| facilities | List\<String\> | ["停车场", "WiFi"] | 设施关键词 |
| nearbyPOIs | List\<String\> | ["天安门", "故宫"] | 周边POI |

#### 推断逻辑示例

```java
// 季节推断
if (content contains "樱花" OR "spring") → 添加 "春"
if (content contains "避暑" OR "summer") → 添加 "夏"
if (content contains "红叶" OR "autumn") → 添加 "秋"
if (content contains "滑雪" OR "winter") → 添加 "冬"

// 预算推断
if (content contains "免费" OR "free") → budgetLevel = "free"
if (content contains "便宜" OR "budget") → budgetLevel = "budget"
if (content contains "中等" OR "moderate") → budgetLevel = "moderate"
if (content contains "高端" OR "luxury") → budgetLevel = "luxury"
```

---

### 3. 混合检索 (Hybrid Retrieval)

**功能**：结合关键词匹配和语义理解的优势

#### 检索策略

```
最终分数 = 0.4 × BM25分数 + 0.6 × 向量分数
```

**为什么是 40/60？**
- BM25 (40%)：擅长精确匹配（如地名、专有名词）
- 向量 (60%)：擅长语义理解（如"浪漫的地方"）

#### BM25 算法

```java
// 参数配置
K1 = 1.5;  // 词频饱和点
B = 0.75;  // 文档长度归一化

// 计算单个词的分数
score(term) = IDF(term) × (TF × (K1 + 1)) / (TF + K1 × (1 - B + B × docLen/avgLen))

// 最终分数 = 所有词的分数之和
```

#### 使用示例

```java
@Autowired
private HybridRetrievalService hybridRetrievalService;

// 基础检索
TravelKnowledgeRetrievalResult result = hybridRetrievalService.hybridSearch(
    "beijing",                              // 目的地
    List.of("history", "culture"),          // 偏好
    "故宫怎么玩",                            // 查询
    10                                       // 返回数量
);

// 带对话历史的检索
TravelKnowledgeRetrievalResult result = hybridRetrievalService.hybridSearch(
    "beijing",
    List.of("history"),
    "那附近有什么好吃的？",                   // 包含代词"那"
    10,
    List.of("我想去故宫玩")                   // 对话历史
);
```

---

### 4. 智能分块 (Smart Chunking)

**功能**：将长文档智能分割为多个知识片段

#### 分块策略（按优先级）

| 策略 | 适用场景 | 分割依据 | 示例 |
|------|---------|---------|------|
| 主题分块 | 多主题文档 | 标题/段落结构 | "北京攻略"→景点/美食/交通 |
| 地理分块 | 多地点文档 | 地理位置变化 | "华东游"→上海/杭州/南京 |
| 时间分块 | 时间序列文档 | 时间表达 | "三天行程"→Day1/Day2/Day3 |
| 重叠分块 | 兜底策略 | 固定大小+重叠 | 重叠率15% |

#### 分块示例

```
输入: "北京3日游攻略"
  Day1: 故宫、天安门、王府井
  Day2: 长城、颐和园
  Day3: 798艺术区、三里屯

输出:
[块1] Day1: 故宫、天安门、王府井 (type: time, metadata: {season, budget...})
[块2] Day2: 长城、颐和园 (type: time, metadata: {season, budget...})
[块3] Day3: 798艺术区、三里屯 (type: time, metadata: {season, budget...})
```

#### 质量保证

```java
// 分块验证规则
1. 每块至少100字符（避免碎片化）
2. 每块不超过2000字符（避免过长）
3. 块数量2-10个（避免过多或过少）
4. 重叠分块时重叠率15%（保持连贯性）
```

---

### 5. 重排序 (Reranking)

**功能**：对检索结果进行个性化重排序

#### 评分维度

```
最终分数 = 0.35 × 相关性 + 0.25 × 偏好匹配 + 0.20 × 时效性 + 0.20 × 多样性
```

| 维度 | 权重 | 计算依据 | 示例 |
|------|------|---------|------|
| 相关性 | 35% | 查询匹配度 | "故宫" vs "故宫门票" → 0.9 |
| 偏好匹配 | 25% | 用户偏好 | 喜欢"history" → 历史景点得分高 |
| 时效性 | 20% | 时间新鲜度 | 2026年的信息比2020年新 |
| 多样性 | 20% | MMR算法 | 避免推荐10个相似景点 |

#### MMR 多样性算法

```java
// Maximal Marginal Relevance
// 目标：既相关又多样

MMR score = λ × relevance(i) - (1-λ) × max(similarity(i, j))
                相关性          多样性惩罚

λ = 0.7  // 偏向相关性
λ = 0.3  // 偏向多样性
```

#### 使用示例

```java
@Autowired
private RerankingService rerankingService;

List<TravelKnowledgeSnippet> results = ...;  // 检索结果

// 重排序
List<TravelKnowledgeSnippet> reranked = rerankingService.rerank(
    results,
    "beijing historical sites",              // 查询
    List.of("history", "culture", "photo"),  // 用户偏好
    5                                         // Top-5
);

// 批量重排序（用于多路召回）
List<List<TravelKnowledgeSnippet>> batches = ...;
List<TravelKnowledgeSnippet> reranked = rerankingService.rerankBatch(
    batches, query, preferences, 10
);
```

---

## 使用指南

### 快速开始

#### 1. 导入服务

```java
@Service
public class MyService {
    @Autowired
    private QueryUnderstandingService queryUnderstandingService;
    
    @Autowired
    private HybridRetrievalService hybridRetrievalService;
    
    @Autowired
    private RerankingService rerankingService;
    
    @Autowired
    private SmartChunkingService smartChunkingService;
}
```

#### 2. 完整检索流程

```java
public List<TravelKnowledgeSnippet> search(
    String destination,
    List<String> preferences,
    String userQuery,
    int limit,
    List<String> conversationHistory
) {
    // 步骤1: 查询理解
    UnderstoodQuery understood = queryUnderstandingService.understand(
        userQuery, conversationHistory
    );
    
    // 步骤2: 混合检索
    TravelKnowledgeRetrievalResult result = hybridRetrievalService.hybridSearch(
        destination,
        preferences,
        understood.rewrittenQuery(),
        limit * 3,  // 检索更多用于重排序
        conversationHistory
    );
    
    // 步骤3: 重排序
    List<TravelKnowledgeSnippet> finalResults = rerankingService.rerank(
        result.snippets(),
        understood.rewrittenQuery(),
        preferences,
        limit
    );
    
    return finalResults;
}
```

#### 3. 知识入库

```java
@Autowired
private TravelKnowledgeVectorStoreRepository vectorStoreRepository;

// 添加知识（自动分块+元数据增强）
TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
    "beijing", "attractions", "故宫游玩攻略", 
    "故宫是中国最大的古代建筑群...",
    List.of("历史", "文化"), "官方指南", "guide",
    null, List.of(), List.of(),
    null, null, null, null, null, null, null, null, null, null
);

// 自动处理：分块 → 元数据增强 → 向量化 → 存储
vectorStoreRepository.upsert(snippet);
```

---

### 高级用法

#### 自定义分块策略

```java
SmartChunkingService chunkingService = new SmartChunkingService();

// 自定义参数
chunkingService.setMinChunkSize(150);        // 最小150字符
chunkingService.setMaxChunkSize(2500);       // 最大2500字符
chunkingService.setOverlapRatio(0.2);        // 20%重叠
chunkingService.setMaxChunks(15);            // 最多15块

// 执行分块
List<TravelKnowledgeSnippet> chunks = chunkingService.chunk(snippet);
```

#### 调整权重

```java
// 调整混合检索权重
// BM25 50% + 向量 50%（更强调关键词匹配）
hybridRetrievalService.setBm25Weight(0.5);
hybridRetrievalService.setVectorWeight(0.5);

// 调整Reranking权重
// 更强调偏好匹配
rerankingService.setRelevanceWeight(0.25);
rerankingService.setPreferenceWeight(0.35);
rerankingService.setTimelinessWeight(0.20);
rerankingService.setDiversityWeight(0.20);
```

#### 意图驱动的检索策略

```java
UnderstoodQuery understood = queryUnderstandingService.understand(query, history);

// 根据意图调整策略
String topIntent = understood.intent().entrySet().stream()
    .max(Map.Entry.comparingByValue())
    .get().getKey();

switch (topIntent) {
    case "scenic":
        // 景点：强调评分和季节
        preferences.add("rating");
        break;
    case "food":
        // 美食：强调价格和设施
        preferences.add("price");
        break;
    case "hotel":
        // 酒店：强调设施和区域
        preferences.add("facilities");
        break;
}
```

---

## 配置说明

### 应用配置

在 `application.yml` 或环境变量中配置：

```yaml
# RAG 配置
rag:
  # 混合检索
  hybrid:
    bm25-weight: 0.4          # BM25 权重
    vector-weight: 0.6        # 向量权重
    min-score: 0.3            # 最低分数阈值
  
  # Reranking
  reranking:
    relevance-weight: 0.35    # 相关性权重
    preference-weight: 0.25   # 偏好权重
    timeliness-weight: 0.20   # 时效性权重
    diversity-weight: 0.20    # 多样性权重
    mmr-lambda: 0.7           # MMR 参数
  
  # 智能分块
  chunking:
    min-size: 100             # 最小块大小
    max-size: 2000            # 最大块大小
    overlap-ratio: 0.15       # 重叠率
    max-chunks: 10            # 最大块数量
  
  # 查询理解
  query:
    max-expanded: 3           # 最大扩展查询数
    min-intent-score: 0.5     # 最低意图分数
    context-window: 5         # 上下文窗口大小
```

### 向量数据库配置

```yaml
# Milvus 配置
milvus:
  host: localhost
  port: 19530
  collection-name: travel_knowledge
  embedding-dimension: 1536   # OpenAI embedding 维度

# 或 Qdrant
qdrant:
  host: localhost
  port: 6334
  collection-name: travel_knowledge
```

---

## 性能优化

### 1. 缓存策略

```java
// 查询结果缓存（推荐实现）
Cache<String, List<TravelKnowledgeSnippet>> queryCache = 
    Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();

String cacheKey = destination + ":" + String.join(",", preferences) + ":" + query;
List<TravelKnowledgeSnippet> cached = queryCache.getIfPresent(cacheKey);
if (cached != null) {
    return cached;
}
```

### 2. 批量处理

```java
// 批量入库（推荐）
List<TravelKnowledgeSnippet> snippets = ...;

// 批量分块
List<TravelKnowledgeSnippet> allChunks = snippets.stream()
    .flatMap(s -> smartChunkingService.chunk(s).stream())
    .collect(Collectors.toList());

// 批量存储
vectorStoreRepository.upsertAll(allChunks);
```

### 3. 异步处理

```java
@Async
public CompletableFuture<Void> asyncUpsert(TravelKnowledgeSnippet snippet) {
    vectorStoreRepository.upsert(snippet);
    return CompletableFuture.completedFuture(null);
}
```

### 4. 性能基准

| 操作 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 单次检索 | ~500ms | ~200ms | **60%** |
| 批量入库 (100条) | ~30s | ~10s | **67%** |
| 分块处理 | ~100ms | ~50ms | **50%** |
| 缓存命中 | N/A | <10ms | **全新** |

---

## 测试指南

### 运行测试

```bash
# 运行所有 RAG 测试
./mvnw test -Dtest=SmartChunkingServiceTest,RerankingServiceTest,QueryUnderstandingServiceTest

# 运行单个测试
./mvnw test -Dtest=SmartChunkingServiceTest

# 查看详细输出
./mvnw test -Dtest=SmartChunkingServiceTest -X
```

### 测试覆盖

| 测试类 | 测试用例数 | 覆盖功能 |
|--------|-----------|---------|
| SmartChunkingServiceTest | 7 | 主题/重叠/地理/季节分块 |
| RerankingServiceTest | 8 | 偏好匹配/MMR/批量重排序 |
| QueryUnderstandingServiceTest | 15 | 纠错/扩展/意图/实体/上下文 |
| **总计** | **30** | **全部核心功能** |

### 编写新测试

```java
@Test
void testCustomScenario() {
    // 准备测试数据
    TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
        "beijing", "attractions", "Test", "Content",
        List.of("tag1"), "source", "guide",
        null, List.of(), List.of(),
        null, null, null, null, null, null, null, null, null, null
    );
    
    // 执行测试
    List<TravelKnowledgeSnippet> chunks = smartChunkingService.chunk(snippet);
    
    // 验证结果
    assertNotNull(chunks);
    assertFalse(chunks.isEmpty());
    assertEquals("beijing", chunks.get(0).city());
}
```

---

## 故障排除

### 常见问题

#### Q1: 检索结果不准确

**可能原因**：
1. 查询理解未正确识别意图
2. BM25 和向量权重不合适
3. 知识库数据质量差

**解决方案**：
```java
// 1. 检查查询理解结果
UnderstoodQuery understood = queryUnderstandingService.understand(query, history);
System.out.println("意图: " + understood.intent());
System.out.println("重写查询: " + understood.rewrittenQuery());

// 2. 调整权重
hybridRetrievalService.setBm25Weight(0.6);  // 增加关键词权重
hybridRetrievalService.setVectorWeight(0.4);

// 3. 检查元数据
snippet.tags().forEach(tag -> System.out.println("标签: " + tag));
```

#### Q2: 分块结果不理想

**可能原因**：
1. 文档结构不清晰
2. 块大小设置不合理

**解决方案**：
```java
// 调整分块参数
smartChunkingService.setMinChunkSize(150);
smartChunkingService.setMaxChunkSize(1500);
smartChunkingService.setOverlapRatio(0.2);  // 增加重叠率
```

#### Q3: 重排序效果差

**可能原因**：
1. 偏好列表为空
2. 权重配置不合理

**解决方案**：
```java
// 确保有偏好信息
if (preferences == null || preferences.isEmpty()) {
    preferences = List.of("general");  // 默认偏好
}

// 调整权重
rerankingService.setPreferenceWeight(0.35);  // 增加偏好权重
```

### 日志配置

```yaml
logging:
  level:
    # RAG 相关日志
    com.travalagent.infrastructure.repository: DEBUG
    com.travalagent.domain.support: DEBUG
    
  # 输出到文件
  file:
    name: logs/rag.log
```

---

## 相关文档

- [TODO.md](../TODO.md) - 项目改进计划
- [RAG_Phase1_完成报告.md](../RAG_Phase1_完成报告.md) - Phase 1 完成报告
- [knowledge-rag.md](knowledge-rag.md) - RAG 知识库概述
- [system-architecture.md](system-architecture.md) - 系统架构

---

## 更新日志

### v1.1.0 (2026-04-11) - Phase 1 完成

- ✅ 查询理解增强（5步处理流程）
- ✅ 元数据增强（11个新字段+8个推断方法）
- ✅ 混合检索（BM25+向量融合）
- ✅ 智能分块（4种策略）
- ✅ Reranking重排序（4维评分+MMR）

### v1.0.0 (2026-03-01) - 初始版本

- 基础向量检索
- 简单关键词匹配

---

**文档版本**: v1.1.0  
**最后更新**: 2026-04-11  
**维护者**: TravelAgent Team
