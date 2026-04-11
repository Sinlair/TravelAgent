# TravelAgent 设计模式文档

本文档详细记录了 TravelAgent 项目中使用的设计模式，帮助开发者理解系统架构和设计决策。

## 📋 目录

- [架构设计模式](#架构设计模式)
- [领域驱动设计模式](#领域驱动设计模式)
- [行为设计模式](#行为设计模式)
- [创建型设计模式](#创建型设计模式)
- [结构型设计模式](#结构型设计模式)
- [AI 增强设计模式](#ai-增强设计模式)
  - [12. 自修复模式](#12-自修复模式)
  - [13. 检索增强生成模式 (RAG)](#13-检索增强生成模式-rag)
- [组合与结构模式](#组合与结构模式)
  - [14. 组合模式](#14-组合模式)
  - [15. 数据传输对象模式 (DTO)](#15-数据传输对象模式-dto)
  - [16. 依赖注入模式](#16-依赖注入模式)
  - [17. 适配器模式](#17-适配器模式)
  - [18. 外观模式](#18-外观模式)
- [管道模式](#管道模式)
  - [19. 行程生成管道](#19-行程生成管道)
- [其他实用模式](#其他实用模式)
  - [20. 缓存模式](#20-缓存模式)
  - [21. 限流模式](#21-限流模式)
  - [22. 规格模式](#22-规格模式)
- [模式协作关系](#模式协作关系)

---

## 架构设计模式

### 1. 分层架构模式 (Layered Architecture Pattern)

**定义**: 将系统划分为多个层次，每一层都有明确的职责边界，层与层之间通过定义良好的接口进行通信。

**项目实现**:

```
travel-agent-domain          # 领域层 - 核心业务逻辑
├── model/                   #   实体和值对象
├── repository/              #   仓储接口
├── gateway/                 #   网关接口
├── service/                 #   领域服务接口
└── event/                   #   事件发布接口

travel-agent-app             # 应用层 - 应用编排
├── controller/              #   REST API 控制器
├── service/                 #   工作流编排
├── dto/                     #   数据传输对象
└── stream/                  #   SSE 流式传输

travel-agent-infrastructure  # 基础设施层 - 技术实现
├── repository/              #   仓储实现
├── gateway/                 #   网关实现
└── config/                  #   配置类

travel-agent-amap            # 外部网关层 - 第三方集成
└── gateway/                 #   高德地图 API 封装
```

**设计原则**:
- 依赖倒置：高层模块不依赖低层模块，都依赖抽象
- 单向依赖：外层依赖内层，内层不依赖外层
- 领域纯粹性：领域层不包含任何技术框架代码

**代码示例**:
```java
// 领域层定义接口
public interface ConversationRepository {
    Optional<ConversationSession> findConversation(String conversationId);
    void saveConversation(ConversationSession session);
}

// 基础设施层实现接口
@Repository
public class SqliteConversationRepository implements ConversationRepository {
    // 具体实现...
}
```

**优势**:
- ✅ 清晰的职责划分
- ✅ 易于测试和替换实现
- ✅ 领域逻辑不受技术细节污染
- ✅ 支持渐进式重构

---

### 2. 端口-适配器模式 (Ports and Adapters / Hexagonal Architecture)

**定义**: 通过端口（接口）将应用程序核心与外部关注点隔离，适配器负责将外部系统的协议转换为应用核心可理解的协议。

**项目实现**:

**端口（领域层接口）**:
```java
// 文件: travel-agent-domain/src/main/java/.../gateway/AmapGateway.java
public interface AmapGateway {
    WeatherSnapshot weather(String city);
    GeoLocation geocode(String address);
    List<PlaceSuggestion> inputTips(PlaceSearchQuery query);
    TransitRoutePlan transitRoute(TransitRouteQuery query);
}
```

**适配器（基础设施层实现）**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../tool/AmapMcpGateway.java
@Component
public class AmapMcpGateway {
    private final Map<String, ToolCallback> callbacks;
    
    public GeoLocation geocode(String address, String conversationId) {
        return convertValue(call("amap_geocode", Map.of("address", address), conversationId), 
                           GeoLocation.class);
    }
    // 将 MCP 工具调用适配为领域网关接口
}
```

**端口类型**:
- **输入端口**: 应用层暴露给外部的接口（如 Controller）
- **输出端口**: 领域层定义的接口（如 Repository、Gateway）

**适配器类型**:
- **输入适配器**: Web 控制器、消息监听器
- **输出适配器**: 数据库访问、HTTP 客户端、消息生产者

**优势**:
- ✅ 领域核心与外部依赖解耦
- ✅ 易于更换外部系统实现
- ✅ 支持模拟测试
- ✅ 技术栈可独立演进

---

## 领域驱动设计模式

### 3. 仓储模式 (Repository Pattern)

**定义**: 在领域层和数据映射层之间提供一个类集合的接口，用于访问聚合根对象。

**项目实现**:

**仓储接口（领域层）**:
```java
// 文件: travel-agent-domain/src/main/java/.../repository/ConversationRepository.java
public interface ConversationRepository {
    Optional<ConversationSession> findConversation(String conversationId);
    List<ConversationSession> listConversations();
    void saveConversation(ConversationSession session);
    void saveMessage(ConversationMessage message);
    List<ConversationMessage> findMessages(String conversationId);
    // ... 其他方法
}
```

**仓储实现（基础设施层）**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../repository/SqliteConversationRepository.java
@Repository
public class SqliteConversationRepository implements ConversationRepository {
    
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    
    @Override
    public Optional<ConversationSession> findConversation(String conversationId) {
        return jdbcClient.sql("""
            SELECT conversation_id, title, agent_type, summary, created_at, updated_at
            FROM conversation_session
            WHERE conversation_id = :conversationId
            """)
            .param("conversationId", conversationId)
            .query(this::mapConversation)
            .optional();
    }
    
    // 数据映射方法
    private ConversationSession mapConversation(ResultSet rs, int rowNum) throws SQLException {
        String agentType = rs.getString("agent_type");
        return new ConversationSession(
            rs.getString("conversation_id"),
            rs.getString("title"),
            agentType == null ? null : AgentType.valueOf(agentType),
            rs.getString("summary"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
    }
}
```

**仓储职责**:
- 封装数据访问逻辑
- 提供领域对象的持久化机制
- 返回领域实体而非数据记录
- 隐藏底层数据库技术细节

**优势**:
- ✅ 领域层不依赖具体数据库技术
- ✅ 统一的集合风格 API
- ✅ 易于替换存储实现（SQLite → MySQL → MongoDB）
- ✅ 便于单元测试（可 mock）

---

### 4. 网关模式 (Gateway Pattern)

**定义**: 封装对外部系统或服务的访问，提供统一的接口。

**项目实现**:

**网关接口**:
```java
// 文件: travel-agent-domain/src/main/java/.../gateway/AmapGateway.java
public interface AmapGateway {
    WeatherSnapshot weather(String city);
    GeoLocation geocode(String address);
    GeoLocation reverseGeocode(String longitude, String latitude);
    List<PlaceSuggestion> inputTips(PlaceSearchQuery query);
    TransitRoutePlan transitRoute(TransitRouteQuery query);
}
```

**网关实现**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../tool/AmapMcpGateway.java
@Component
public class AmapMcpGateway {
    private final Map<String, ToolCallback> callbacks;
    private final Map<String, Map<String, JsonNode>> conversationCache = new ConcurrentHashMap<>();
    
    public GeoLocation geocode(String address, String conversationId) {
        return convertValue(call("amap_geocode", Map.of("address", address), conversationId), 
                           GeoLocation.class);
    }
    
    public List<PlaceSuggestion> inputTips(PlaceSearchQuery query, String conversationId) {
        // 调用高德地图 MCP 工具
        JsonNode node = call("amap_input_tips", arguments, conversationId);
        return objectMapper.convertValue(node, PLACE_SUGGESTION_LIST);
    }
}
```

**网关职责**:
- 封装外部 API 调用
- 协议转换（MCP → 领域模型）
- 错误处理和重试
- 缓存和限流

**与仓储的区别**:
- **仓储**: 访问内部数据存储
- **网关**: 访问外部系统服务

---

### 5. 实体与值对象模式 (Entity & Value Object)

**实体 (Entity)**: 具有唯一标识的对象，其身份在整个生命周期中保持不变。

**值对象 (Value Object)**: 通过属性值来定义的对象，没有唯一标识，不可变。

**项目实现**:

**实体示例**:
```java
// ConversationSession 实体 - 有唯一标识 conversationId
public record ConversationSession(
    String conversationId,      // 唯一标识
    String title,
    AgentType lastAgent,
    String summary,
    Instant createdAt,
    Instant updatedAt
) {}

// ConversationMessage 实体
public record ConversationMessage(
    String id,                  // 唯一标识
    String conversationId,
    MessageRole role,
    String content,
    AgentType agentType,
    Instant createdAt,
    Map<String, Object> metadata
) {}
```

**值对象示例**:
```java
// GeoLocation 值对象 - 通过属性值定义，不可变
public record GeoLocation(
    String formattedAddress,
    String longitude,
    String latitude,
    String province,
    String city,
    String district
) {}

// WeatherSnapshot 值对象
public record WeatherSnapshot(
    String city,
    String weather,
    String temperature,
    String windDirection,
    String windPower,
    String humidity,
    String reportTime
) {}

// AgentType 值对象（枚举）
public enum AgentType {
    WEATHER,
    GEO,
    TRAVEL_PLANNER,
    GENERAL
}
```

**设计要点**:
- **实体**: 关注身份和生命周期
- **值对象**: 关注属性，不可变，可自由替换
- **值对象优势**: 线程安全、易于测试、避免副作用

---

## 行为设计模式

### 6. 策略模式 (Strategy Pattern)

**定义**: 定义一系列算法，将每个算法封装起来，并使它们可以互换。

**项目实现**:

**策略接口**:
```java
// 文件: travel-agent-domain/src/main/java/.../service/SpecialistAgent.java
public interface SpecialistAgent {
    // 返回支持的智能体类型
    AgentType supports();
    
    // 执行智能体逻辑
    AgentExecutionResult execute(AgentExecutionContext context);
}
```

**具体策略实现**:
```java
// 天气智能体
@Component
public class WeatherAgent extends AbstractOpenAiSpecialistAgent {
    public WeatherAgent(ChatClient.Builder chatClientBuilder, 
                       OpenAiAvailability openAiAvailability) {
        super(chatClientBuilder, openAiAvailability);
    }
    
    @Override
    public AgentType supports() {
        return AgentType.WEATHER;
    }
    
    @Override
    protected String systemPrompt() {
        return "你是一个专业的天气查询助手...";
    }
}

// 地理智能体
@Component
public class GeoAgent extends AbstractOpenAiSpecialistAgent {
    @Override
    public AgentType supports() {
        return AgentType.GEO;
    }
    
    @Override
    protected String systemPrompt() {
        return "你是一个地理信息查询专家...";
    }
}

// 旅行规划智能体
@Component
public class TravelPlannerAgent extends AbstractOpenAiSpecialistAgent {
    @Override
    public AgentType supports() {
        return AgentType.TRAVEL_PLANNER;
    }
    
    @Override
    protected String systemPrompt() {
        return "你是一个专业的旅行规划师...";
    }
}

// 通用智能体
@Component
public class GeneralAgent extends AbstractOpenAiSpecialistAgent {
    @Override
    public AgentType supports() {
        return AgentType.GENERAL;
    }
    
    @Override
    protected String systemPrompt() {
        return "你是一个通用的旅行助手...";
    }
}
```

**策略上下文**:
```java
// 文件: travel-agent-app/src/main/java/.../service/ConversationWorkflow.java
@Component
public class ConversationWorkflow {
    
    // 通过构造函数注入所有策略实现
    private final Map<AgentType, SpecialistAgent> specialistAgents;
    
    public ConversationWorkflow(
            List<SpecialistAgent> specialistAgents,  // Spring 自动注入所有实现
            // ... 其他依赖
    ) {
        // 将 List 转换为 Map，以 AgentType 为键
        this.specialistAgents = specialistAgents.stream()
            .collect(Collectors.toMap(SpecialistAgent::supports, Function.identity()));
    }
    
    // 动态选择策略执行
    private AgentExecutionResult executeAgent(AgentType agentType, 
                                             AgentExecutionContext context) {
        SpecialistAgent agent = specialistAgents.get(agentType);
        if (agent == null) {
            throw new IllegalArgumentException("Unsupported agent type: " + agentType);
        }
        return agent.execute(context);
    }
}
```

**使用场景**:
```java
// 1. 路由决策
AgentRouteDecision decision = agentRouter.route(routingContext);

// 2. 根据决策选择策略
AgentExecutionResult result = executeAgent(decision.agentType(), executionContext);
```

**优势**:
- ✅ 符合开闭原则（添加新策略无需修改现有代码）
- ✅ 消除条件判断语句
- ✅ 策略可独立测试
- ✅ 运行时可动态切换策略

---

### 7. 路由模式 (Router Pattern)

**定义**: 根据输入内容的特征，将其分发到不同的处理器。

**项目实现**:

**路由接口**:
```java
// 文件: travel-agent-domain/src/main/java/.../service/AgentRouter.java
public interface AgentRouter {
    AgentRouteDecision route(RoutingContext context);
}
```

**路由实现**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../llm/OpenAiAgentRouter.java
@Component
public class OpenAiAgentRouter implements AgentRouter {
    
    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;
    
    @Override
    public AgentRouteDecision route(RoutingContext context) {
        // 优先使用 LLM 路由
        if (!openAiAvailability.isAvailable()) {
            return heuristic(context.userMessage());  // 降级到启发式路由
        }
        
        try {
            RouterOutput output = chatClientBuilder.build()
                .prompt()
                .system("""
                    You are the router for a travel assistant.
                    You must choose exactly one agent from WEATHER, GEO, 
                    TRAVEL_PLANNER, or GENERAL.
                    Rules:
                    1. Weather, temperature, rain → WEATHER
                    2. Geocoding, addresses, coordinates → GEO
                    3. Itinerary planning, budget → TRAVEL_PLANNER
                    4. Everything else → GENERAL
                    """)
                .user("""
                    User request: %s
                    Task memory: %s
                    Conversation summary: %s
                    """.formatted(
                        context.userMessage(),
                        context.taskMemory(),
                        context.conversationSummary()
                    ))
                .call()
                .entity(RouterOutput.class);
            
            return new AgentRouteDecision(
                output.agentType(),
                output.reason(),
                output.clarificationRequired(),
                output.clarificationQuestion()
            );
        } catch (Exception e) {
            return heuristic(context.userMessage());  // 异常时降级
        }
    }
    
    // 启发式路由（基于关键词匹配）
    private AgentRouteDecision heuristic(String message) {
        String content = message.toLowerCase();
        
        if (containsAny(content, "weather", "temperature", "天气", "温度")) {
            return new AgentRouteDecision(AgentType.WEATHER, "keyword route: weather", 
                                         false, null);
        }
        if (containsAny(content, "coordinate", "address", "经纬度", "地址")) {
            return new AgentRouteDecision(AgentType.GEO, "keyword route: geo", 
                                         false, null);
        }
        if (containsAny(content, "trip", "itinerary", "旅行", "行程")) {
            return new AgentRouteDecision(AgentType.TRAVEL_PLANNER, 
                                         "keyword route: planner", false, null);
        }
        return new AgentRouteDecision(AgentType.GENERAL, "fallback route", false, null);
    }
}
```

**路由决策结果**:
```java
public record AgentRouteDecision(
    AgentType agentType,              // 目标智能体
    String reason,                    // 路由原因
    boolean clarificationRequired,    // 是否需要澄清
    String clarificationQuestion      // 澄清问题
) {}
```

**降级策略**:
```
LLM 路由 → 启发式路由 → 默认路由
 (主策略)   (关键词匹配)  (GENERAL)
```

**优势**:
- ✅ 智能路由（LLM 理解语义）
- ✅ 容错降级（多层降级保障）
- ✅ 可解释性（记录路由原因）
- ✅ 支持澄清交互

---

### 8. 观察者模式 / 事件发布模式 (Observer / Event Publisher Pattern)

**定义**: 定义对象间的一对多依赖关系，当一个对象状态改变时，所有依赖者自动收到通知。

**项目实现**:

**事件发布接口**:
```java
// 文件: travel-agent-domain/src/main/java/.../event/TimelinePublisher.java
public interface TimelinePublisher {
    void publish(TimelineEvent event);
}
```

**事件实体**:
```java
public record TimelineEvent(
    String id,
    String conversationId,
    ExecutionStage stage,        // 执行阶段
    String message,              // 事件消息
    Map<String, Object> details, // 详细信息
    Instant createdAt
) {}
```

**事件发布使用**:
```java
// 在 ConversationWorkflow 中发布事件
@Component
public class ConversationWorkflow {
    
    private final TimelinePublisher timelinePublisher;
    
    public ChatResponse execute(ChatRequest request) {
        // 1. 发布开始事件
        timelinePublisher.publish(new TimelineEvent(
            UUID.randomUUID().toString(),
            conversationId,
            ExecutionStage.ROUTING,
            "正在分析您的需求...",
            Map.of("userMessage", request.message()),
            Instant.now()
        ));
        
        // 2. 执行业务逻辑
        AgentRouteDecision decision = agentRouter.route(context);
        
        // 3. 发布路由完成事件
        timelinePublisher.publish(new TimelineEvent(
            UUID.randomUUID().toString(),
            conversationId,
            ExecutionStage.AGENT_EXECUTION,
            "已分配专家智能体: " + decision.agentType(),
            Map.of("agentType", decision.agentType()),
            Instant.now()
        ));
        
        // ... 继续执行
    }
}
```

**事件订阅者（SSE 推送）**:
```java
// 基础设施层实现事件发布，推送到前端
@Component
public class SseTimelinePublisher implements TimelinePublisher {
    
    private final SseEmitterRegistry emitterRegistry;
    
    @Override
    public void publish(TimelineEvent event) {
        // 保存事件到数据库
        conversationRepository.saveTimeline(event);
        
        // 推送 SSE 事件到前端
        SseEmitter emitter = emitterRegistry.getEmitter(event.conversationId());
        if (emitter != null) {
            emitter.send(SseEmitter.event()
                .name("timeline")
                .data(event));
        }
    }
}
```

**执行阶段枚举**:
```java
public enum ExecutionStage {
    ROUTING,              // 路由阶段
    AGENT_EXECUTION,      // 智能体执行
    PLAN_BUILDING,        // 行程构建
    PLAN_VALIDATION,      // 行程校验
    PLAN_REPAIR,          // 行程修复
    COMPLETED             // 完成
}
```

**优势**:
- ✅ 实时状态推送（SSE）
- ✅ 解耦事件生产者和消费者
- ✅ 支持多个订阅者
- ✅ 便于调试和监控

---

### 9. 模板方法模式 (Template Method Pattern)

**定义**: 在父类中定义算法骨架，将某些步骤延迟到子类实现。

**项目实现**:

**抽象模板类**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../llm/AbstractOpenAiSpecialistAgent.java
abstract class AbstractOpenAiSpecialistAgent implements SpecialistAgent {
    
    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;
    private final ToolCallbackProvider[] toolCallbackProviders;
    
    // 模板方法：定义执行流程
    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) {
        // 1. 检查服务可用性
        if (!openAiAvailability.isAvailable()) {
            return fallback(context, new IllegalStateException("OpenAI API key is missing"));
        }
        
        try {
            String content;
            ChatClient.ChatClientRequestSpec prompt = chatClientBuilder.build()
                .prompt()
                .system(systemPrompt());  // ← 抽象方法，子类实现
            
            // 2. 构建用户提示
            if (context.imageAttachments().isEmpty()) {
                prompt = prompt.user(buildUserPrompt(context));
            } else {
                prompt = prompt.user(user -> user
                    .text(buildUserPrompt(context))
                    .media(ImageAttachmentMediaSupport.toMediaArray(
                        context.imageAttachments())));
            }
            
            // 3. 执行 LLM 调用
            if (toolCallbackProviders.length > 0) {
                content = prompt.toolCallbacks(toolCallbackProviders)
                    .toolContext(Map.of("conversationId", context.conversationId()))
                    .call()
                    .content();
            } else {
                content = prompt.call().content();
            }
            
            // 4. 返回结果
            return new AgentExecutionResult(
                supports(),
                content == null ? "I could not produce a result" : content,
                metadata(context, false, null),
                null
            );
        } catch (Exception exception) {
            // 5. 异常时降级
            return fallback(context, exception);
        }
    }
    
    // 抽象方法：子类必须实现
    protected abstract String systemPrompt();
    
    // 钩子方法：子类可覆盖
    protected AgentExecutionResult fallback(AgentExecutionContext context, 
                                           Exception exception) {
        return new AgentExecutionResult(
            supports(),
            fallbackAnswer(context, exception),  // ← 可覆盖
            metadata(context, true, exception),
            null
        );
    }
    
    // 钩子方法：提供默认实现
    protected String fallbackAnswer(AgentExecutionContext context, Exception exception) {
        if (containsChinese(context.userMessage())) {
            return "模型服务暂时不可用，请稍后再试。";
        }
        return "The model service is temporarily unavailable.";
    }
    
    // 共享方法：构建用户提示
    protected String buildUserPrompt(AgentExecutionContext context) {
        return """
            Respond in the user's language.
            
            Latest user request: %s
            Structured task memory: %s
            Conversation summary: %s
            Long-term memory: %s
            Recent messages: %s
            """.formatted(
                context.userMessage(),
                renderTaskMemory(context),
                renderLongTermMemory(context),
                renderRecentMessages(context)
            );
    }
}
```

**具体实现**:
```java
// 天气智能体 - 只需实现 systemPrompt
@Component
public class WeatherAgent extends AbstractOpenAiSpecialistAgent {
    
    public WeatherAgent(ChatClient.Builder chatClientBuilder, 
                       OpenAiAvailability openAiAvailability) {
        super(chatClientBuilder, openAiAvailability);
    }
    
    @Override
    public AgentType supports() {
        return AgentType.WEATHER;
    }
    
    @Override
    protected String systemPrompt() {
        return """
            你是一个专业的天气查询助手。
            根据用户的问题，查询并返回天气信息。
            使用友好、简洁的语言回复。
            """;
    }
}

// 旅行规划智能体 - 自定义降级答案
@Component
public class TravelPlannerAgent extends AbstractOpenAiSpecialistAgent {
    
    @Override
    protected String fallbackAnswer(AgentExecutionContext context, Exception exception) {
        if (containsChinese(context.userMessage())) {
            return "抱歉，行程规划服务暂时不可用。请稍后再试，或联系人工客服。";
        }
        return "Sorry, the travel planning service is temporarily unavailable.";
    }
}
```

**执行流程**:
```
execute()
  ├─ 检查服务可用性
  ├─ 调用 systemPrompt()      ← 子类实现
  ├─ 构建用户提示
  ├─ 执行 LLM 调用
  ├─ 返回结果
  └─ 异常时调用 fallback()    ← 可覆盖
```

**优势**:
- ✅ 代码复用（共享逻辑在父类）
- ✅ 扩展点明确（子类只需关注差异）
- ✅ 执行流程可控（模板方法定义骨架）
- ✅ 符合开闭原则

---

## 创建型设计模式

### 10. 工厂模式 (Factory Pattern)

**定义**: 定义一个创建对象的接口，但由子类决定要实例化哪个类。

**项目实现**:

**Spring 隐式工厂**:
```java
// Spring 容器作为对象工厂，通过 @Component 和 @Bean 注册
@Component
public class WeatherAgent implements SpecialistAgent { }

@Component
public class GeoAgent implements SpecialistAgent { }

@Component
public class TravelPlannerAgent implements SpecialistAgent { }
```

**显式工厂方法**:
```java
// 文件: travel-agent-app/src/main/java/.../service/ConversationWorkflow.java
@Component
public class ConversationWorkflow {
    
    private final Map<AgentType, SpecialistAgent> specialistAgents;
    
    // Spring 自动注入所有 SpecialistAgent 实现
    public ConversationWorkflow(List<SpecialistAgent> specialistAgents) {
        // 工厂方法：将 List 转换为 Map
        this.specialistAgents = specialistAgents.stream()
            .collect(Collectors.toMap(
                SpecialistAgent::supports,  // 键：AgentType
                Function.identity()         // 值：智能体实例
            ));
    }
    
    // 工厂方法：根据类型获取智能体
    private SpecialistAgent getAgent(AgentType type) {
        SpecialistAgent agent = specialistAgents.get(type);
        if (agent == null) {
            throw new IllegalArgumentException("Unsupported agent type: " + type);
        }
        return agent;
    }
}
```

**优势**:
- ✅ 集中管理对象创建
- ✅ 易于扩展新类型
- ✅ 支持依赖注入

---

### 11. 构建器模式 (Builder Pattern)

**定义**: 将复杂对象的构建与表示分离，使得同样的构建过程可以创建不同的表示。

**项目实现**:

**Spring AI ChatClient.Builder**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../llm/OpenAiAgentRouter.java
@Component
public class OpenAiAgentRouter implements AgentRouter {
    
    private final ChatClient.Builder chatClientBuilder;
    
    @Override
    public AgentRouteDecision route(RoutingContext context) {
        // 使用构建器模式构建 ChatClient 和 Prompt
        RouterOutput output = chatClientBuilder.build()  // 构建 ChatClient
            .prompt()                                     // 创建 Prompt
            .system("""                                   // 设置系统提示
                You are the router for a travel assistant.
                """)
            .user("""                                     // 设置用户提示
                User request: %s
                """.formatted(context.userMessage()))
            .call()                                       // 执行调用
            .entity(RouterOutput.class);                  // 转换为实体
        
        return new AgentRouteDecision(
            output.agentType(),
            output.reason(),
            output.clarificationRequired(),
            output.clarificationQuestion()
        );
    }
}
```

**构建器链式调用**:
```java
chatClientBuilder.build()           // 1. 构建客户端
    .prompt()                       // 2. 创建提示词
    .system(systemPrompt)           // 3. 设置系统提示
    .user(userPrompt)               // 4. 设置用户提示
    .toolCallbacks(providers)       // 5. 设置工具回调（可选）
    .toolContext(context)           // 6. 设置工具上下文（可选）
    .call()                         // 7. 执行调用
    .entity(OutputClass.class);     // 8. 转换结果
```

**优势**:
- ✅ 链式调用，代码简洁
- ✅ 可选参数灵活配置
- ✅ 构建过程清晰
- ✅ 避免构造函数参数爆炸

---

## AI 增强设计模式

### 12. 自修复模式 (Self-Healing / Repairer Pattern)

**定义**: 在系统生成结果后，通过校验器发现错误，并利用修复器自动进行修正，直到满足预设约束。

**项目实现**:

**执行逻辑**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../llm/TravelPlannerAgent.java
public AgentExecutionResult execute(AgentExecutionContext context) {
    // 1. 生成初始方案
    TravelPlan draftPlan = travelPlanBuilder.build(context);
    
    // 2. 校验方案
    TravelPlanValidationResult initialValidation = validatePlan(enrichedPlan, context, 0);
    
    // 3. 循环修复（最多尝试 N 次）
    RepairCycleResult strictCycle = repairUntilAccepted(
        initialValidation, context, MAX_REPAIR_ATTEMPTS, 0, false);
    
    // 4. 如果严格模式失败，尝试放宽约束进行修复
    if (!strictCycle.validationResult().accepted()) {
        RepairCycleResult relaxedCycle = repairUntilAccepted(
            strictCycle.validationResult(), context, MAX_RELAXED_ATTEMPTS, 
            strictCycle.repairAttempts(), true);
    }
}
```

**组件**:
- **Validator ([HeuristicTravelPlanValidator.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/HeuristicTravelPlanValidator.java))**: 检查时间冲突、预算超支等。
- **Repairer ([HeuristicTravelPlanRepairer.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/HeuristicTravelPlanRepairer.java))**: 针对校验失败项，生成修复指令并调用 LLM 重新生成部分内容。

**优势**:
- ✅ 显著提高 AI 生成结果的准确性
- ✅ 能够处理复杂的硬性约束（如预算）
- ✅ 提供优雅的降级（放宽约束）

---

### 13. 检索增强生成模式 (RAG - Retrieval Augmented Generation)

**定义**: 在调用大模型前，先从知识库中检索相关上下文，并将其注入提示词，以增强模型的专业性。

**项目实现**:

**检索流程**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../repository/TravelKnowledgeVectorStoreRepository.java
public TravelKnowledgeRetrievalResult search(String query, int limit) {
    // 1. 将查询词转换为向量
    float[] embedding = embeddingModel.embed(query);
    
    // 2. 在向量数据库（Milvus）中进行相似度搜索
    SearchParam searchParam = SearchParam.newBuilder()
        .withCollectionName(COLLECTION_NAME)
        .withVectors(List.of(embedding))
        .withTopK(limit)
        .build();
    
    // 3. 返回最相关的旅行知识片段
}
```

**知识注入**:
- **[TravelPlannerAgent.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/TravelPlannerAgent.java)**：在生成行程前，会根据目的地检索当地的景点、美食、酒店等真实数据，注入到 Prompt 中。

**优势**:
- ✅ 减少 AI 幻觉（基于真实知识）
- ✅ 能够获取模型训练截止日期后的最新信息
- ✅ 提高行程的落地性和专业度

---

## 结构型设计模式

### 14. 组合模式 (Composite Pattern - 结构化数据)

**定义**: 将对象组合成树形结构以表示“部分-整体”的层次结构。

**项目实现**:

**领域模型结构**:
- **TravelPlan ([TravelPlan.java](file:///e:/Internship/program/TravelAgent/travel-agent-domain/src/main/java/com/travalagent/domain/model/entity/TravelPlan.java))**: 顶层聚合根。
  - **TravelPlanDay ([TravelPlanDay.java](file:///e:/Internship/program/TravelAgent/travel-agent-domain/src/main/java/com/travalagent/domain/model/entity/TravelPlanDay.java))**: 每日计划列表。
    - **TravelPlanSlot ([TravelPlanSlot.java](file:///e:/Internship/program/TravelAgent/travel-agent-domain/src/main/java/com/travalagent/domain/model/entity/TravelPlanSlot.java))**: 时间段（上午/下午/晚上）。
      - **TravelPlanStop ([TravelPlanStop.java](file:///e:/Internship/program/TravelAgent/travel-agent-domain/src/main/java/com/travalagent/domain/model/entity/TravelPlanStop.java))**: 具体景点/餐厅。
        - **TravelTransitLeg ([TravelTransitLeg.java](file:///e:/Internship/program/TravelAgent/travel-agent-domain/src/main/java/com/travalagent/domain/model/entity/TravelTransitLeg.java))**: 交通路线段。

**优势**:
- ✅ 这种树形结构使得行程规划可以逐级细化。
- ✅ 方便对整个行程或特定日期进行统一处理（如重新排序、费用计算）。

---

### 15. 数据传输对象模式 (DTO Pattern)

**定义**: 在不同层（如前端与后端）之间传递数据的对象，通常不包含业务逻辑。

**项目实现**:

**DTO 定义**:
- **[ChatRequest.java](file:///e:/Internship/program/TravelAgent/travel-agent-app/src/main/java/com/travalagent/app/dto/ChatRequest.java)**: 封装前端发送的聊天消息。
- **[ChatResponse.java](file:///e:/Internship/program/TravelAgent/travel-agent-app/src/main/java/com/travalagent/app/dto/ChatResponse.java)**: 封装返回给前端的流式或非流式响应。
- **[ConversationDetailResponse.java](file:///e:/Internship/program/TravelAgent/travel-agent-app/src/main/java/com/travalagent/app/dto/ConversationDetailResponse.java)**: 封装对话详情，避免直接暴露领域实体。

**优势**:
- ✅ 解耦前端展示逻辑与后端业务逻辑。
- ✅ 减少网络传输的数据量（只传递前端需要的字段）。
- ✅ 提供安全性（避免敏感数据暴露）。

---

### 16. 依赖注入模式 (Dependency Injection Pattern)

**定义**: 将对象的依赖关系从外部注入，而不是在对象内部创建。

**项目实现**:

**构造函数注入**:
```java
// 文件: travel-agent-app/src/main/java/.../service/ConversationWorkflow.java
@Component
public class ConversationWorkflow {
    
    // 声明依赖
    private final ConversationRepository conversationRepository;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final AgentRouter agentRouter;
    private final TaskMemoryExtractor taskMemoryExtractor;
    private final ConversationSummarizer conversationSummarizer;
    private final ImageAttachmentInterpreter imageAttachmentInterpreter;
    private final Map<AgentType, SpecialistAgent> specialistAgents;
    private final TimelinePublisher timelinePublisher;
    private final TravelAgentProperties properties;
    
    // 构造函数注入（Spring 自动装配）
    public ConversationWorkflow(
            ConversationRepository conversationRepository,
            LongTermMemoryRepository longTermMemoryRepository,
            AgentRouter agentRouter,
            TaskMemoryExtractor taskMemoryExtractor,
            ConversationSummarizer conversationSummarizer,
            ImageAttachmentInterpreter imageAttachmentInterpreter,
            List<SpecialistAgent> specialistAgents,
            TimelinePublisher timelinePublisher,
            TravelAgentProperties properties
    ) {
        this.conversationRepository = conversationRepository;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.agentRouter = agentRouter;
        this.taskMemoryExtractor = taskMemoryExtractor;
        this.conversationSummarizer = conversationSummarizer;
        this.imageAttachmentInterpreter = imageAttachmentInterpreter;
        this.specialistAgents = specialistAgents.stream()
            .collect(Collectors.toMap(SpecialistAgent::supports, Function.identity()));
        this.timelinePublisher = timelinePublisher;
        this.properties = properties;
    }
}
```

**注入方式**:
- ✅ **构造函数注入**（推荐）：不可变依赖，易于测试
- ❌ 字段注入（`@Autowired`）：不推荐，难以测试
- ⚠️ Setter 注入：可选依赖

**优势**:
- ✅ 解耦依赖关系
- ✅ 易于单元测试（可 mock）
- ✅ 依赖关系明确
- ✅ 支持不可变对象

---

### 17. 适配器模式 (Adapter Pattern)

**定义**: 将一个类的接口转换成客户期望的另一个接口。

**项目实现**:

**MCP 工具回调适配器**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../tool/AmapMcpGateway.java
@Component
public class AmapMcpGateway {
    
    private final ObjectMapper objectMapper;
    private final Map<String, ToolCallback> callbacks;
    
    public AmapMcpGateway(
            ObjectMapper objectMapper,
            @Qualifier("amapToolCallbackProvider") ToolCallbackProvider toolCallbackProvider
    ) {
        this.objectMapper = objectMapper;
        // 适配：将 MCP ToolCallback 转换为领域网关接口
        this.callbacks = List.of(toolCallbackProvider.getToolCallbacks()).stream()
            .collect(Collectors.toMap(
                callback -> callback.getToolDefinition().name(),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }
    
    // 领域接口：geocode(address)
    public GeoLocation geocode(String address, String conversationId) {
        // 适配：转换为 MCP 工具调用
        return convertValue(
            call("amap_geocode", Map.of("address", address), conversationId),
            GeoLocation.class
        );
    }
    
    // 适配：MCP 工具返回 JSON → 领域值对象
    private <T> T convertValue(JsonNode node, Class<T> targetType) {
        return objectMapper.convertValue(node, targetType);
    }
}
```

**适配过程**:
```
领域网关接口                    MCP 工具回调
   geocode()              →    amap_geocode tool
   inputTips()            →    amap_input_tips tool
   transitRoute()         →    amap_transit_route tool
       ↓                          ↓
  领域值对象              →    JSON 响应
  GeoLocation             →    {"longitude": "...", "latitude": "..."}
```

**优势**:
- ✅ 隔离外部 API 变化
- ✅ 统一接口风格
- ✅ 便于替换实现

---

### 18. 外观模式 (Facade Pattern)

**定义**: 为子系统中的一组接口提供一个统一的界面，简化使用。

**项目实现**:

**ConversationWorkflow 作为外观**:
```java
// 文件: travel-agent-app/src/main/java/.../service/ConversationWorkflow.java
@Component
public class ConversationWorkflow {
    
    // 内部依赖多个子系统
    private final ConversationRepository conversationRepository;
    private final AgentRouter agentRouter;
    private final TaskMemoryExtractor taskMemoryExtractor;
    private final Map<AgentType, SpecialistAgent> specialistAgents;
    private final TimelinePublisher timelinePublisher;
    
    // 外观方法：简化复杂流程
    @Transactional
    public ChatResponse execute(ChatRequest request) {
        // 1. 准备会话
        PreparedConversation prepared = prepareConversation(
            request.conversationId(),
            request.message(),
            request.attachments()
        );
        
        // 2. 构建上下文
        MemoryContext memoryContext = buildMemoryContext(
            prepared.conversationId(),
            request.message()
        );
        
        // 3. 路由决策
        AgentRouteDecision decision = agentRouter.route(
            new RoutingContext(request.message(), memoryContext)
        );
        
        // 4. 执行智能体
        AgentExecutionResult result = executeAgent(
            decision.agentType(),
            buildExecutionContext(request, memoryContext, decision)
        );
        
        // 5. 处理结果
        ChatResponse response = buildResponse(result, prepared);
        
        // 6. 发布事件
        timelinePublisher.publish(buildCompletedEvent(response));
        
        return response;
    }
}
```

**外部调用简化**:
```java
// 控制器只需调用一个方法
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    
    private final ConversationWorkflow conversationWorkflow;
    
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @RequestBody ChatRequest request) {
        // 外观模式：一行代码完成复杂流程
        ChatResponse response = conversationWorkflow.execute(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

**封装的子系统**:
```
ConversationWorkflow (外观)
  ├─ ConversationRepository      (会话管理)
  ├─ AgentRouter                 (智能路由)
  ├─ TaskMemoryExtractor         (记忆提取)
  ├─ SpecialistAgent             (专家执行)
  ├─ TravelPlanBuilder           (行程构建)
  ├─ TravelPlanValidator         (行程校验)
  ├─ TravelPlanRepairer          (行程修复)
  └─ TimelinePublisher           (事件发布)
```

**优势**:
- ✅ 简化客户端调用
- ✅ 封装复杂流程
- ✅ 降低耦合度
- ✅ 统一事务管理

---

## 管道模式 (Pipeline Pattern)

### 19. 行程生成管道 (Travel Plan Generation Pipeline)

**定义**: 将复杂的处理逻辑分解为多个独立的阶段，每个阶段对数据进行处理并传递给下一个阶段。

**项目实现**:

**执行链 ([TravelPlannerAgent.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/TravelPlannerAgent.java))**:
1. **Builder (构建器)**: [ConstraintDrivenTravelPlanBuilder.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/ConstraintDrivenTravelPlanBuilder.java) 生成初步行程。
2. **Enricher (增强器)**: [AmapTravelPlanEnricher.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/AmapTravelPlanEnricher.java) 调用外部 API 补充交通、天气等详情。
3. **Validator (校验器)**: [HeuristicTravelPlanValidator.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/HeuristicTravelPlanValidator.java) 检查行程逻辑一致性。
4. **Repairer (修复器)**: [HeuristicTravelPlanRepairer.java](file:///e:/Internship/program/TravelAgent/travel-agent-infrastructure/src/main/java/com/travalagent/infrastructure/gateway/llm/HeuristicTravelPlanRepairer.java) 自动修正校验发现的问题。

**优势**:
- ✅ 将原本庞大的 LLM 逻辑拆解为可管理的组件。
- ✅ 每个阶段都可以独立测试（如 Mock API 返回值）。
- ✅ 容易在管道中插入新阶段（如添加“景点避坑指南”增强器）。

---

## 其他实用模式

### 20. 缓存模式 (Cache Pattern)

**定义**: 将频繁访问的数据存储在快速访问的存储层，减少重复计算或外部调用。

**项目实现**:

```java
// 文件: travel-agent-infrastructure/src/main/java/.../tool/AmapMcpGateway.java
@Component
public class AmapMcpGateway {
    
    // 会话级缓存：按会话隔离
    private final Map<String, Map<String, JsonNode>> conversationCache = 
        new ConcurrentHashMap<>();
    
    private JsonNode call(String toolName, Map<String, Object> arguments, 
                         String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            // 构建缓存键
            String cacheKey = cacheKey(toolName, arguments);
            
            // 获取会话缓存
            Map<String, JsonNode> toolCache = conversationCache
                .computeIfAbsent(conversationId, ignored -> new ConcurrentHashMap<>());
            
            // 检查缓存
            JsonNode cached = toolCache.get(cacheKey);
            if (cached != null) {
                return cached.deepCopy();  // 缓存命中
            }
            
            // 执行调用
            throttleBeforeCall();
            String raw = callback.call(writeJson(arguments), 
                                      new ToolContext(Map.of("conversationId", conversationId)));
            JsonNode result = unwrap(raw);
            
            // 写入缓存
            toolCache.put(cacheKey, result.deepCopy());
            return result;
        }
        
        // 无缓存逻辑...
    }
    
    // 缓存键生成（参数排序保证一致性）
    private String cacheKey(String toolName, Map<String, Object> arguments) {
        Map<String, Object> sortedArguments = new TreeMap<>(arguments);
        return toolName + ":" + writeJson(sortedArguments);
    }
    
    // 清理会话缓存
    public void clearConversationCache(String conversationId) {
        conversationCache.remove(conversationId);
    }
}
```

**缓存策略**:
- **缓存键**: `工具名:排序后的参数JSON`
- **缓存范围**: 会话级别（不同会话独立缓存）
- **缓存清理**: 会话结束时清理
- **线程安全**: `ConcurrentHashMap`

**优势**:
- ✅ 减少重复 API 调用
- ✅ 降低响应延迟
- ✅ 节省 API 配额

---

### 21. 限流模式 (Throttling Pattern)

**定义**: 控制请求处理速率，防止系统过载或超过外部 API 限制。

**项目实现**:

```java
// 文件: travel-agent-infrastructure/src/main/java/.../tool/AmapMcpGateway.java
@Component
public class AmapMcpGateway {
    
    private static final long MIN_TOOL_CALL_INTERVAL_MS = 380L;  // 最小间隔 380ms
    
    private final Object throttleMonitor = new Object();  // 锁对象
    private long lastToolCallAt = 0L;                      // 上次调用时间
    
    private void throttleBeforeCall() {
        synchronized (throttleMonitor) {
            long now = System.currentTimeMillis();
            long waitMillis = (lastToolCallAt + MIN_TOOL_CALL_INTERVAL_MS) - now;
            
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);  // 等待
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                        "Interrupted while throttling Amap MCP requests", exception);
                }
            }
            
            lastToolCallAt = System.currentTimeMillis();  // 更新时间
        }
    }
}
```

**限流算法**: 固定延迟（Fixed Delay）

**执行流程**:
```
调用请求 → 检查间隔 → 等待（如需） → 执行调用 → 更新时间
```

**优势**:
- ✅ 防止 API 限流
- ✅ 保护外部服务
- ✅ 线程安全

---

### 22. 规格模式 (Specification Pattern)

**定义**: 将业务规则封装为可组合的规格对象，用于校验对象是否满足条件。

**项目实现**:

**行程校验器**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../llm/HeuristicTravelPlanValidator.java
@Component
public class HeuristicTravelPlanValidator {
    
    public TravelPlanValidationResult validate(TravelPlan plan) {
        List<String> violations = new ArrayList<>();
        
        // 规格 1：天数匹配
        if (!validateDaysMatch(plan)) {
            violations.add("行程天数与用户需求不匹配");
        }
        
        // 规格 2：每天至少一个景点
        if (!validateDailyAttractions(plan)) {
            violations.add("某些天没有安排景点");
        }
        
        // 规格 3：预算合理性
        if (!validateBudget(plan)) {
            violations.add("预算分配不合理");
        }
        
        // 规格 4：地理位置连续性
        if (!validateGeoContinuity(plan)) {
            violations.add("景点地理位置跳跃过大");
        }
        
        return new TravelPlanValidationResult(violations.isEmpty(), violations);
    }
    
    private boolean validateDaysMatch(TravelPlan plan) {
        return plan.days().size() == plan.requestedDays();
    }
    
    private boolean validateDailyAttractions(TravelPlan plan) {
        return plan.days().stream()
            .allMatch(day -> !day.attractions().isEmpty());
    }
}
```

**行程修复器**:
```java
// 文件: travel-agent-infrastructure/src/main/java/.../llm/HeuristicTravelPlanRepairer.java
@Component
public class HeuristicTravelPlanRepairer {
    
    public TravelPlan repair(TravelPlan plan, List<String> violations) {
        TravelPlan repaired = plan;
        
        for (String violation : violations) {
            if (violation.contains("天数不匹配")) {
                repaired = repairDaysMismatch(repaired);
            }
            if (violation.contains("没有安排景点")) {
                repaired = addMissingAttractions(repaired);
            }
            if (violation.contains("预算不合理")) {
                repaired = adjustBudget(repaired);
            }
        }
        
        return repaired;
    }
}
```

**规格组合**:
```
校验器
  ├─ 天数匹配规格
  ├─ 景点覆盖规格
  ├─ 预算合理规格
  └─ 地理连续规格

修复器
  ├─ 天数修复策略
  ├─ 景点补充策略
  └─ 预算调整策略
```

**优势**:
- ✅ 业务规则独立可测
- ✅ 规格可组合复用
- ✅ 校验和修复分离

---

## 模式协作关系

### 完整请求处理流程

```
用户请求
   ↓
[外观模式] ConversationWorkflow.execute()
   ↓
[策略模式] AgentRouter.route() → 决定使用哪个智能体
   ↓
[策略模式] SpecialistAgent.execute() → 执行智能体逻辑
   │
   ├─ [模板方法] AbstractOpenAiSpecialistAgent.execute()
   │     ├─ 调用 systemPrompt() (子类实现)
   │     ├─ 构建 Prompt
   │     └─ 调用 LLM
   │
   └─ [适配器模式] AmapMcpGateway.geocode()
         ├─ [缓存模式] 检查缓存
         ├─ [限流模式] 控制调用频率
         └─ 调用 MCP 工具
   ↓
[仓储模式] ConversationRepository.saveMessage()
   ↓
[观察者模式] TimelinePublisher.publish() → SSE 推送前端
   ↓
返回响应
```

### 依赖关系图

```
┌─────────────────────────────────────────────────────────┐
│  应用层 (Application Layer)                              │
│  ┌──────────────────────────────────────────────────┐   │
│  │ ConversationWorkflow (外观模式)                   │   │
│  │  ├─ 依赖 AgentRouter (策略模式)                   │   │
│  │  ├─ 依赖 SpecialistAgent (策略模式)               │   │
│  │  ├─ 依赖 ConversationRepository (仓储模式)        │   │
│  │  └─ 依赖 TimelinePublisher (观察者模式)           │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
   ↓ 依赖
┌─────────────────────────────────────────────────────────┐
│  领域层 (Domain Layer)                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │ Entity       │  │ Value Object │  │ Domain Service│ │
│  │ - Session    │  │ - GeoLocation│  │ - AgentRouter │ │
│  │ - Message    │  │ - Weather    │  │ - Specialist  │ │
│  │ - TravelPlan │  │ - AgentType  │  │ - PlanBuilder │ │
│  └──────────────┘  └──────────────┘  └───────────────┘ │
│  ┌──────────────────────────────────────────────────┐   │
│  │ 端口 (Ports)                                      │   │
│  │  ├─ ConversationRepository (接口)                 │   │
│  │  ├─ AmapGateway (接口)                            │   │
│  │  └─ TimelinePublisher (接口)                      │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
   ↑ 实现
┌─────────────────────────────────────────────────────────┐
│  基础设施层 (Infrastructure Layer)                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │ 适配器 (Adapters)                                 │   │
│  │  ├─ SqliteConversationRepository (仓储实现)       │   │
│  │  ├─ AmapMcpGateway (网关实现)                     │   │
│  │  ├─ OpenAiAgentRouter (路由实现)                  │   │
│  │  ├─ WeatherAgent (策略实现)                       │   │
│  │  ├─ GeoAgent (策略实现)                           │   │
│  │  └─ SseTimelinePublisher (事件发布实现)           │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 设计模式总结

### 使用的模式清单

| 模式分类 | 模式名称 | 应用场景 | 优势 |
|---------|---------|---------|------|
| **架构模式** | 分层架构 | 项目整体结构 | 职责清晰、易于维护 |
| **架构模式** | 端口-适配器 | 外部依赖隔离 | 领域纯粹、易于替换 |
| **领域模式** | 仓储模式 | 数据持久化 | 抽象存储、易于测试 |
| **领域模式** | 网关模式 | 外部系统访问 | 封装协议、统一接口 |
| **领域模式** | 实体与值对象 | 领域建模 | 表达力强、类型安全 |
| **行为模式** | 策略模式 | 多智能体路由 | 易于扩展、消除条件 |
| **行为模式** | 路由模式 | 意图识别 | 智能分发、容错降级 |
| **行为模式** | 观察者模式 | 事件推送 | 实时通知、解耦 |
| **行为模式** | 模板方法 | 智能体基类 | 代码复用、流程可控 |
| **创建模式** | 工厂模式 | 智能体创建 | 集中管理、依赖注入 |
| **创建模式** | 构建器模式 | ChatClient 构建 | 链式调用、参数灵活 |
| **结构模式** | 依赖注入 | 对象装配 | 解耦依赖、易于测试 |
| **结构模式** | 适配器模式 | MCP 工具适配 | 隔离变化、统一接口 |
| **结构模式** | 外观模式 | 工作流编排 | 简化调用、封装复杂 |
| **实用模式** | 缓存模式 | API 结果缓存 | 减少调用、提升性能 |
| **实用模式** | 限流模式 | API 调用控制 | 防止限流、保护服务 |
| **实用模式** | 规格模式 | 行程校验修复 | 规则独立、可组合 |

### 设计原则遵循

- ✅ **单一职责原则 (SRP)**: 每个类只做一件事
- ✅ **开闭原则 (OCP)**: 对扩展开放，对修改关闭
- ✅ **里氏替换原则 (LSP)**: 子类可替换父类
- ✅ **接口隔离原则 (ISP)**: 接口精简易用
- ✅ **依赖倒置原则 (DIP)**: 依赖抽象而非具体
- ✅ **组合复用原则 (CRP)**: 优先组合而非继承

---

## 扩展指南

### 如何添加新的智能体？

1. **创建智能体类**（继承模板）:
```java
@Component
public class FoodAgent extends AbstractOpenAiSpecialistAgent {
    
    public FoodAgent(ChatClient.Builder chatClientBuilder, 
                    OpenAiAvailability openAiAvailability) {
        super(chatClientBuilder, openAiAvailability);
    }
    
    @Override
    public AgentType supports() {
        return AgentType.FOOD;  // 在枚举中添加新类型
    }
    
    @Override
    protected String systemPrompt() {
        return "你是一个美食推荐专家...";
    }
}
```

2. **在枚举中添加类型**:
```java
public enum AgentType {
    WEATHER,
    GEO,
    TRAVEL_PLANNER,
    GENERAL,
    FOOD  // 新增
}
```

3. **更新路由规则**:
```java
// 在 OpenAiAgentRouter 的 system prompt 中添加规则
// 5. Food, restaurant, local cuisine → FOOD
```

4. **无需修改其他代码**（符合开闭原则）

### 如何替换存储实现？

1. **创建新的仓储实现**:
```java
@Repository
public class MongoConversationRepository implements ConversationRepository {
    // MongoDB 实现
}
```

2. **禁用旧实现**:
```java
// 移除 @Repository 或添加 @ConditionalOnProperty
```

3. **无需修改领域层和应用层**（依赖倒置）

---

## 参考资料

- **领域驱动设计**: Eric Evans - Domain-Driven Design
- **企业应用架构模式**: Martin Fowler - Patterns of Enterprise Application Architecture
- **设计模式**: Gang of Four - Design Patterns
- **整洁架构**: Robert C. Martin - Clean Architecture
- **Spring 最佳实践**: Spring.io Documentation

---

**文档版本**: v1.0  
**最后更新**: 2026-04-11  
**维护者**: TravelAgent 团队
