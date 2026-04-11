# TravelAgent 开发指南

## 📖 目录

- [环境搭建](#环境搭建)
- [项目结构](#项目结构)
- [开发流程](#开发流程)
- [代码规范](#代码规范)
- [测试指南](#测试指南)
- [调试技巧](#调试技巧)
- [贡献指南](#贡献指南)

---

## 环境搭建

### 前置要求

| 工具 | 版本 | 用途 | 下载地址 |
|------|------|------|---------|
| JDK | 21+ | Java 运行环境 | [Adoptium](https://adoptium.net/) |
| Maven | 3.9+ | 项目构建 | [Maven](https://maven.apache.org/) |
| Node.js | 18+ | 前端开发 | [Node.js](https://nodejs.org/) |
| Docker | 24+ | 容器化部署 | [Docker](https://www.docker.com/) |
| Git | 2.40+ | 版本控制 | [Git](https://git-scm.com/) |

### 快速开始

```bash
# 1. 克隆项目
git clone https://github.com/TaoT5/travel-agent.git
cd travel-agent

# 2. 配置环境变量
cp .env.travel-agent.example .env.travel-agent
# 编辑 .env.travel-agent 填写必要的 API Key

# 3. 安装依赖
./mvnw clean install  # Linux/Mac
mvnw.cmd clean install  # Windows

# 4. 启动服务
./mvnw spring-boot:run -pl travel-agent-app  # 后端
cd web && npm install && npm run dev  # 前端

# 5. 访问应用
# 前端: http://localhost:3000
# 后端 API: http://localhost:8080
```

### Docker 快速启动

```bash
# 一键启动所有服务（后端+前端+Milvus）
docker-compose -f docker-compose.milvus.yml up -d
docker-compose -f docker-compose.app.yml up -d

# 查看日志
docker-compose -f docker-compose.app.yml logs -f
```

---

## 项目结构

### DDD 架构概述

```
travel-agent/
├── travel-agent-domain/          # 领域层 (核心业务逻辑)
│   └── src/main/java/
│       └── com/travalagent/domain/
│           ├── model/            # 领域模型
│           │   ├── entity/       # 实体
│           │   └── valobj/       # 值对象
│           └── support/          # 领域支持类
│
├── travel-agent-app/             # 应用层 (用例编排)
│   └── src/main/java/
│       └── com/travalagent/app/
│           ├── workflow/         # 工作流编排
│           ├── service/          # 应用服务
│           └── controller/       # REST API
│
├── travel-agent-infrastructure/  # 基础设施层 (技术实现)
│   └── src/main/java/
│       └── com/travalagent/infrastructure/
│           ├── repository/       # 仓储实现
│           │   ├── HybridRetrievalService.java      # 混合检索
│           │   ├── RerankingService.java            # 重排序
│           │   ├── QueryUnderstandingService.java   # 查询理解
│           │   └── SmartChunkingService.java        # 智能分块
│           ├── integration/      # 外部服务集成
│           └── config/           # 配置类
│
├── travel-agent-types/           # 类型定义 (DTO/枚举)
│   └── src/main/java/
│       └── com/travalagent/types/
│           ├── dto/              # 数据传输对象
│           └── enums/            # 枚举定义
│
├── travel-agent-amap/            # 高德地图集成
│   └── src/main/java/
│       └── com/travalagent/amap/
│
└── web/                          # 前端 (Vue 3)
    └── src/
        ├── components/           # 组件
        ├── views/                # 页面
        └── api/                  # API 调用
```

### 核心模块依赖关系

```
web (前端)
  ↓ HTTP
travel-agent-app (应用层)
  ↓ 调用
travel-agent-domain (领域层)
  ↓ 依赖
travel-agent-infrastructure (基础设施层)
  ↓ 使用
travel-agent-types (类型定义)
```

---

## 开发流程

### 1. 创建新特性

```bash
# 1. 创建特性分支
git checkout -b feature/your-feature-name

# 2. 开发功能
# ... 编写代码 ...

# 3. 运行测试
./mvnw test

# 4. 提交代码
git add .
git commit -m "feat: add your feature description"

# 5. 推送到远程
git push origin feature/your-feature-name

# 6. 创建 Pull Request
```

### 2. 添加新的 RAG 功能

```java
// 1. 在 domain 层定义接口
public interface MyNewRetrievalStrategy {
    List<TravelKnowledgeSnippet> retrieve(RetrievalRequest request);
}

// 2. 在 infrastructure 层实现
@Service
public class MyNewRetrievalStrategyImpl implements MyNewRetrievalStrategy {
    @Override
    public List<TravelKnowledgeSnippet> retrieve(RetrievalRequest request) {
        // 实现检索逻辑
        return results;
    }
}

// 3. 在 app 层使用
@Service
public class MyWorkflow {
    @Autowired
    private MyNewRetrievalStrategy retrievalStrategy;
    
    public void execute() {
        List<TravelKnowledgeSnippet> results = 
            retrievalStrategy.retrieve(request);
        // 使用结果
    }
}

// 4. 编写测试
@SpringBootTest
class MyNewRetrievalStrategyTest {
    @Autowired
    private MyNewRetrievalStrategy strategy;
    
    @Test
    void testRetrieve() {
        // 测试逻辑
    }
}
```

### 3. 修改现有功能

```bash
# 1. 找到相关代码
# 使用 IDE 的全局搜索或 grep

# 2. 理解现有逻辑
# 阅读代码和注释，查看测试用例

# 3. 进行修改
# 保持向后兼容性

# 4. 更新测试
# 确保现有测试通过，添加新测试

# 5. 运行完整测试套件
./mvnw clean test
```

---

## 代码规范

### Java 代码风格

#### 命名规范

```java
// 类名: PascalCase
public class HybridRetrievalService { }

// 方法名: camelCase
public List<TravelKnowledgeSnippet> hybridSearch() { }

// 常量: UPPER_SNAKE_CASE
private static final double BM25_WEIGHT = 0.4;

// 变量: camelCase
List<String> expandedQueries = new ArrayList<>();

// 包名: 全小写
com.travalagent.infrastructure.repository
```

#### 注释规范

```java
/**
 * 混合检索服务
 * 
 * 结合 BM25 关键词检索和向量语义检索，提供更准确的检索结果。
 * 
 * @author Your Name
 * @since 2026-04-11
 */
@Service
public class HybridRetrievalService {
    
    /**
     * 执行混合检索
     * 
     * @param destination 目的地
     * @param preferences 用户偏好列表
     * @param query 用户查询
     * @param limit 返回数量限制
     * @param conversationHistory 对话历史（可选）
     * @return 检索结果
     */
    public TravelKnowledgeRetrievalResult hybridSearch(
            String destination,
            List<String> preferences,
            String query,
            int limit,
            List<String> conversationHistory
    ) {
        // 实现细节...
    }
}
```

#### 异常处理

```java
// ✅ 好的做法：明确异常类型
public TravelKnowledgeSnippet process(String content) {
    try {
        return parseSnippet(content);
    } catch (JsonParseException e) {
        log.error("Failed to parse snippet: {}", content, e);
        throw new InvalidSnippetException("Invalid JSON format", e);
    }
}

// ❌ 不好的做法：捕获所有异常
public TravelKnowledgeSnippet process(String content) {
    try {
        return parseSnippet(content);
    } catch (Exception e) {  // 太宽泛
        throw new RuntimeException(e);
    }
}
```

### Git 提交规范

```bash
# 格式: <type>: <description>

# 类型:
# feat:     新功能
# fix:      修复 bug
# docs:     文档更新
# style:    代码格式（不影响功能）
# refactor: 重构
# test:     测试相关
# chore:    构建/工具相关

# 示例:
git commit -m "feat: add query understanding service"
git commit -m "fix: resolve BM25 score calculation issue"
git commit -m "docs: update RAG feature guide"
git commit -m "refactor: extract metadata inference logic"
```

---

## 测试指南

### 测试类型

| 类型 | 位置 | 用途 | 示例 |
|------|------|------|------|
| 单元测试 | `src/test/java/.../repository/` | 测试单个类/方法 | `SmartChunkingServiceTest` |
| 集成测试 | `src/test/java/.../integration/` | 测试多个组件协作 | - |
| 端到端测试 | `web/tests/` | 测试完整用户流程 | - |

### 编写单元测试

```java
@SpringBootTest
class SmartChunkingServiceTest {
    
    @Autowired
    private SmartChunkingService smartChunkingService;
    
    @Test
    void shouldChunkByTopic() {
        // Given: 准备测试数据
        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
            "beijing", "attractions",
            "北京旅游攻略",
            """
            ## 景点推荐
            故宫、长城、颐和园
            
            ## 美食推荐
            烤鸭、炸酱面、豆汁
            """,
            List.of("guide"), "source", "guide",
            null, List.of(), List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        
        // When: 执行分块
        List<TravelKnowledgeSnippet> chunks = smartChunkingService.chunk(snippet);
        
        // Then: 验证结果
        assertNotNull(chunks);
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().anyMatch(c -> c.content().contains("故宫")));
        assertTrue(chunks.stream().anyMatch(c -> c.content().contains("烤鸭")));
    }
    
    @Test
    void shouldHandleEmptyContent() {
        // Given
        TravelKnowledgeSnippet snippet = new TravelKnowledgeSnippet(
            "beijing", "attractions",
            "Empty", "",  // 空内容
            List.of(), "source", "guide",
            null, List.of(), List.of(),
            null, null, null, null, null, null, null, null, null, null
        );
        
        // When
        List<TravelKnowledgeSnippet> chunks = smartChunkingService.chunk(snippet);
        
        // Then
        assertEquals(1, chunks.size());  // 应该保留原始内容
    }
}
```

### 运行测试

```bash
# 运行所有测试
./mvnw test

# 运行特定测试类
./mvnw test -Dtest=SmartChunkingServiceTest

# 运行特定测试方法
./mvnw test -Dtest=SmartChunkingServiceTest#shouldChunkByTopic

# 生成测试覆盖率报告
./mvnw test jacoco:report

# 查看报告
# target/site/jacoco/index.html
```

### Mock 外部依赖

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    
    @Mock
    private VectorStoreRepository vectorStoreRepository;
    
    @InjectMocks
    private MyService myService;
    
    @Test
    void shouldRetrieveFromVectorStore() {
        // Given
        List<TravelKnowledgeSnippet> mockResults = List.of(
            createMockSnippet("故宫"),
            createMockSnippet("长城")
        );
        
        when(vectorStoreRepository.search(any()))
            .thenReturn(mockResults);
        
        // When
        List<TravelKnowledgeSnippet> results = myService.search("beijing");
        
        // Then
        assertEquals(2, results.size());
        verify(vectorStoreRepository).search(any());
    }
}
```

---

## 调试技巧

### 1. 日志调试

```java
// 添加日志
@Slf4j
@Service
public class MyService {
    
    public void process(String query) {
        log.debug("Processing query: {}", query);
        
        try {
            // 处理逻辑
            log.info("Successfully processed query");
        } catch (Exception e) {
            log.error("Failed to process query: {}", query, e);
            throw e;
        }
    }
}
```

```yaml
# 调整日志级别 (application.yml)
logging:
  level:
    root: INFO
    com.travalagent: DEBUG
    com.travalagent.infrastructure.repository: TRACE
```

### 2. 断点调试

```bash
# 1. 以调试模式启动
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

# 2. 在 IDE 中配置 Remote Debug
# - Host: localhost
# - Port: 5005

# 3. 设置断点
# 4. 启动调试
# 5. 触发请求，IDE 会在断点处暂停
```

### 3. 性能分析

```bash
# 1. 启用性能监控
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-XX:+FlightRecorder"

# 2. 记录性能数据
jcmd <pid> JFR.start name=MyRecording duration=60s filename=recording.jfr

# 3. 分析结果
# 使用 JDK Mission Control 打开 recording.jfr
```

---

## 贡献指南

### 1. Fork 项目

```bash
# 1. 在 GitHub 上 Fork 项目
# 2. 克隆你的 Fork
git clone https://github.com/YOUR_USERNAME/travel-agent.git
cd travel-agent

# 3. 添加上游远程仓库
git remote add upstream https://github.com/TaoT5/travel-agent.git
```

### 2. 保持同步

```bash
# 1. 获取上游更新
git fetch upstream

# 2. 切换到主分支
git checkout main

# 3. 合并上游更新
git merge upstream/main

# 4. 推送到你的 Fork
git push origin main
```

### 3. 提交 Pull Request

1. **确保测试通过**
   ```bash
   ./mvnw clean test
   ```

2. **更新文档**
   - 如果添加了新功能，更新相关文档
   - 如果修改了 API，更新 API 文档

3. **编写清晰的 PR 描述**
   ```markdown
   ## 描述
   简要描述这个 PR 做了什么
   
   ## 相关 Issue
   Fixes #123
   
   ## 测试
   - [x] 单元测试通过
   - [x] 手动测试通过
   
   ## 截图（如果适用）
   添加截图展示 UI 变化
   ```

4. **请求 Review**
   - 指定 1-2 位维护者
   - 回复评论并及时修改

---

## 常见问题

### Q: 如何添加新的依赖？

```xml
<!-- 在父 pom.xml 的 <dependencyManagement> 中添加版本管理 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>example-lib</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 在子模块的 pom.xml 中添加依赖 -->
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>example-lib</artifactId>
        <!-- 不需要版本号，继承父 POM -->
    </dependency>
</dependencies>
```

### Q: 如何调试前端？

```bash
# 1. 启动前端开发服务器
cd web
npm run dev

# 2. 打开浏览器开发者工具
# - Chrome: F12 或 Cmd+Option+I
# - Firefox: F12 或 Cmd+Option+I

# 3. 在 Sources 面板设置断点
# 4. 在 Console 面板执行调试命令
```

### Q: 数据库在哪里？

```bash
# 默认使用 H2 文件数据库
data/travel-agent.db

# 查看数据库内容
# 1. 启用 H2 Console (application.yml)
spring:
  h2:
    console:
      enabled: true
      path: /h2-console

# 2. 访问 http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:file:./data/travel-agent.db
# 用户名: sa
# 密码: (空)
```

---

## 相关资源

- [README.md](../README.md) - 项目介绍
- [CONTRIBUTING.md](../CONTRIBUTING.md) - 贡献指南
- [TODO.md](../TODO.md) - 开发计划
- [RAG 功能文档](rag-feature-guide.md) - RAG 详细说明
- [系统架构](system-architecture.md) - 架构设计

---

**文档版本**: v1.0.0  
**最后更新**: 2026-04-11  
**维护者**: TravelAgent Team
