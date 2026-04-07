# Travel Agent

<p align="center">
  <a href="./README.md">English</a> |
  <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <img alt="agent routing" src="https://img.shields.io/badge/Agent-Routing-111827?style=for-the-badge" />
  <img alt="travel planning" src="https://img.shields.io/badge/Structured-Planning-0F766E?style=for-the-badge" />
  <img alt="rag" src="https://img.shields.io/badge/RAG-Travel%20Knowledge-7C3AED?style=for-the-badge" />
  <img alt="amap" src="https://img.shields.io/badge/Amap-Gaode-0284C7?style=for-the-badge" />
</p>

[![CI](https://github.com/Sinlair/TravelAgent/actions/workflows/ci.yml/badge.svg)](https://github.com/Sinlair/TravelAgent/actions/workflows/ci.yml)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-3-003B57?logo=sqlite&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)

一个开源的多智能体旅行规划项目，使用 Spring Boot、Spring AI、Vue 3、SQLite、可选的 Amap MCP 集成，以及面向城市旅行场景的知识 RAG 管线构建。

这个项目不只是一个“能聊天的旅行助手”。它更强调产品化能力：请求会被路由到不同 specialist agent，行程会以结构化方式生成，经过约束校验和修复，再结合天气、地图和本地知识进行补全，并向前端暴露清晰的执行时间线。

## 一眼看懂

- 🤖 多智能体路由：天气、地理、行程规划、通用问答
- 🧭 结构化行程生成：预算、节奏、营业时间、通勤负载校验
- 🗺️ 高德 / Amap 增强：POI、天气、通勤路线、地理解析
- 🧠 旅行知识 RAG：目的地知识检索、来源可追踪、风格偏好提示
- 🖥️ 工程化交付：脚本、Docker、Smoke Test、CI 一起带上

## 目录

- [项目能力](#项目能力)
- [为什么做这个项目](#为什么做这个项目)
- [技术栈](#技术栈)
- [架构说明](#架构说明)
- [快速开始](#快速开始)
- [手动运行](#手动运行)
- [Docker 部署](#docker-部署)
- [测试与 CI](#测试与-ci)
- [旅行知识 RAG](#旅行知识-rag)
- [安全说明](#安全说明)
- [贡献方式](#贡献方式)
- [许可证](#许可证)

## 项目能力

- 在 `WEATHER`、`GEO`、`TRAVEL_PLANNER`、`GENERAL` 四类 agent 之间自动路由
- 生成结构化旅行计划，并附带预算、节奏、营业时间、通勤负载校验
- 用高德 / Amap 对景点、地理位置、路线、天气做增强
- 从本地数据集或 Milvus 中检索目的地旅行知识
- 将对话、任务记忆、时间线和旅行计划持久化到 SQLite
- 提供 Vue 3 前端用于聊天、查看结构化行程、时间线和检索来源

## 推荐质量现状

当前项目还没有对外发布一个严格的离线准确率指标，比如 itinerary accuracy、precision、recall 或人工评测分数。

目前更偏向“工程约束驱动”的质量控制，而不是“已经有标准化评分榜单”的状态：

- 会先做 agent 路由，再执行对应 specialist
- 行程输出是结构化 `travelPlan`，而不是只有一段自然语言
- planner 会经过 validate / repair / revalidate 流程
- 知识检索在向量检索和本地 fallback 上共用一套 retrieval plan
- 高德 / Amap 数据在可用时会参与 POI、天气和路线增强

所以更准确的说法是：项目已经具备可审查、可修复、可约束的推荐机制，但还不等于已经有正式公布的“准确率百分比”。

## Multi-Agent 设计

这里的 multi-agent 是真实运行链路，不是单个 prompt 换几个名字。

运行时流程：

- `ConversationWorkflow` 负责组装上下文、记忆、摘要和长期记忆
- `AgentRouter` 选择最合适的 specialist
- 对应的 `SpecialistAgent` 接管执行
- 最终结果会和 timeline、memory 一起持久化

当前包含的 specialist：

- `WEATHER`
- `GEO`
- `TRAVEL_PLANNER`
- `GENERAL`

路由层采用 LLM 优先、启发式兜底的方式，因此即使模型层不稳定，系统也不会完全失去路由能力。

## 记忆机制

当前记忆机制是分层设计，不是简单地把整段聊天历史原样塞回模型。

- 短期记忆
  - 从对话历史里读取最近一段消息窗口
  - 默认窗口大小是 `12`
- 任务记忆
  - 结构化保存 `origin`、`destination`、`days`、`budget`、`preferences`、`pendingQuestion`
  - 每轮对话后通过 heuristic + 可选 LLM 抽取进行更新
- 会话摘要
  - 当消息数达到阈值后，会生成压缩摘要
  - 默认阈值是 `6`
- 长期记忆
  - 会把摘要存成可检索的长期记忆
  - 存储后端可以在 SQLite 和 Milvus 之间切换

这种分层方式的好处是：planner 不需要每次都重放整段原始对话，也能保留相对稳定的任务状态。

## 为什么做这个项目

很多旅行助手只能返回一段自然语言答案，这个项目更关注“可执行的旅行规划流程”：

- 不只输出文本，还要有结构化行程
- 不只单次生成，还要经过校验和修复
- 不把核心规划过程完全藏起来，而是暴露给 UI 和调试链路
- 不止演示代码，还带脚本、健康检查、Smoke Test、CI 和部署材料

## 技术栈

- 后端
  - Java 21
  - Spring Boot 4
  - Spring WebFlux
  - Spring Validation
  - Spring Boot Actuator
  - JDBC + SQLite
  - Jackson
- AI 与编排
  - Spring AI
  - OpenAI / OpenAI 兼容网关集成
  - MCP（Model Context Protocol）
  - 独立 Amap MCP Server
- 检索与记忆
  - 本地旅行知识数据集
  - 可选 Milvus 向量检索
  - 长期记忆与目的地知识检索
- 前端
  - Vue 3
  - TypeScript
  - Vite
  - Pinia
  - Vitest
  - Vue Test Utils
  - jsdom
- 交付与运维
  - Maven Wrapper
  - npm
  - Docker / Docker Compose
  - Nginx
  - PowerShell 脚本
  - GitHub Actions CI
  - Micrometer + OpenTelemetry
  - 基于 NSSM 的 Windows 服务部署

## 架构说明

高层流程：

1. 用户请求进入 `ConversationWorkflow`
2. 系统组装短期记忆、任务记忆、摘要和长期记忆
3. `AgentRouter` 选择最合适的 specialist
4. specialist 在共享上下文中执行
5. 行程规划请求会经历 generate、enrich、validate、repair、revalidate
6. 最终答案、任务记忆、时间线和结构化行程被持久化

核心模块：

- [`travel-agent-types`](travel-agent-types)：通用响应与异常类型
- [`travel-agent-domain`](travel-agent-domain)：领域模型、值对象、仓储接口、领域服务
- [`travel-agent-amap`](travel-agent-amap)：Amap HTTP 网关与配置
- [`travel-agent-infrastructure`](travel-agent-infrastructure)：Spring AI agent、检索、向量存储、基础设施适配
- [`travel-agent-app`](travel-agent-app)：WebFlux API、健康检查、时间线流式输出、启动逻辑
- [`travel-agent-amap-mcp-server`](travel-agent-amap-mcp-server)：独立 Amap MCP Server
- [`web`](web)：Vue 3 前端

## 当前状态

目前这个仓库已经适合：

- 本地开发
- Demo / 实验用途
- 自托管 MVP 部署
- 发布前 Smoke 验证

当前仓库已经包含：

- 健康检查
- 预检脚本
- Release Smoke 脚本
- 启停脚本
- Docker 部署材料
- GitHub Actions CI

## 运行前准备

- Java 21
- Node.js / npm
- 如果要跑容器化或 Milvus，需要 Docker Desktop
- 如果要重跑知识采集与清洗，需要 Python 3

目前自动化脚本以 Windows PowerShell 为主，但核心应用并不只限于 Windows。

## 配置说明

复制 [`.env.travel-agent.example`](.env.travel-agent.example) 为 `.env.travel-agent`，再填写你需要的配置项。

示例配置默认走 `LOCAL` 模式，便于本地快速启动。

重点环境变量：

- OpenAI / 兼容网关
  - `SPRING_AI_OPENAI_API_KEY`
  - `SPRING_AI_OPENAI_BASE_URL`
  - `SPRING_AI_OPENAI_CHAT_MODEL`
  - `SPRING_AI_OPENAI_EMBEDDING_MODEL`
  - `SPRING_PROFILES_ACTIVE=prod`
- 高德 / 工具模式
  - `TRAVEL_AGENT_TOOL_PROVIDER=LOCAL|MCP`
  - `TRAVEL_AGENT_AMAP_MCP_ENABLED=true|false`
  - `TRAVEL_AGENT_AMAP_API_KEY`
  - `TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND`
- 检索
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED`
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI`
  - `TRAVEL_AGENT_KNOWLEDGE_VECTOR_COLLECTION_NAME`
  - `TRAVEL_AGENT_MILVUS_ENABLED`
  - `TRAVEL_AGENT_MILVUS_URI`
- 前端
  - `VITE_AMAP_WEB_KEY`
  - `VITE_AMAP_SECURITY_JS_CODE`

说明：

- `SPRING_AI_OPENAI_BASE_URL` 用来接 OpenAI 兼容网关
- `TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND` 默认是 `3.0`，用于约束高德 HTTP 调用频率
- `.env.travel-agent` 已被 Git 忽略，不能提交

## 快速开始

### 1. 预检

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

检查内容包括：

- Java 是否可用
- OpenAI key 是否存在 / 是否像占位符
- MCP 模式下目标服务是否可达
- 清洗后的知识数据是否存在
- 按需检查后端健康状态

如果存在 `.env.travel-agent`，预检脚本会自动加载。

如果想显式指定环境文件：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1 -EnvFile .env.travel-agent.example -SkipHealthCheck
```

### 2. 本地快速启动

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -RunPreflight -ToolProvider LOCAL
```

停止：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

默认本地地址：

- 后端：`http://localhost:18080`
- 前端：`http://localhost:4173`

### 3. Release Smoke

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1
```

会验证：

- 后端能启动
- actuator health 有响应
- planner API wrapper 成功
- 返回结构化 `travelPlan`
- 包含 weather snapshot
- 包含知识检索结果
- 前端构建产物存在

## 手动运行

### 后端

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "<your-openai-key>"
.\mvnw.cmd -pl travel-agent-app -am spring-boot:run
```

生产环境建议：

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
```

如果使用 OpenAI 兼容网关：

```powershell
$env:SPRING_AI_OPENAI_BASE_URL = "https://api.example.com"
$env:SPRING_AI_OPENAI_CHAT_MODEL = "gpt-5.4"
```

### 独立 Amap MCP Server

```powershell
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-amap-mcp-server -am spring-boot:run
```

### 前端

```powershell
cd web
npm.cmd ci
npm.cmd run dev
```

## Docker 部署

内置材料：

- [`Dockerfile.app`](Dockerfile.app)
- [`Dockerfile.mcp`](Dockerfile.mcp)
- [`docker-compose.app.yml`](docker-compose.app.yml)
- [`docker-compose.milvus.yml`](docker-compose.milvus.yml)
- [`web/Dockerfile`](web/Dockerfile)
- [`web/nginx.conf`](web/nginx.conf)

启动应用栈：

```powershell
docker compose -f docker-compose.app.yml up --build -d
```

默认容器端口：

- backend: `8080`
- MCP server: `8090`
- frontend: `8088`

启用 MCP profile：

```powershell
docker compose -f docker-compose.app.yml --profile mcp up --build -d
```

需要向量检索时，单独启动 Milvus：

```powershell
docker compose -f docker-compose.milvus.yml up -d
```

## 测试与 CI

GitHub Actions 工作流：

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- 对 `main` push、PR、手动触发都会运行
- 会跑后端 Maven 测试和前端 test/build

后端测试：

```powershell
.\mvnw.cmd -B test
```

前端测试：

```powershell
cd web
npm.cmd ci
npm.cmd run test
npm.cmd run build
```

## 健康检查与可观测性

Actuator 端点：

- `GET /actuator/health`

当前健康项包括：

- `openAi`
- `toolProvider`
- `knowledgeDataset`
- `knowledgeVector`

Tracing 相关配置：

- `OTEL_EXPORTER_OTLP_ENDPOINT`
- `TRAVEL_AGENT_TRACING_SAMPLING_PROBABILITY`

## 旅行知识 RAG

知识文件：

- [`travel-agent-infrastructure/src/main/resources/travel-knowledge.json`](travel-agent-infrastructure/src/main/resources/travel-knowledge.json)
- [`travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json`](travel-agent-infrastructure/src/main/resources/travel-knowledge.collected.json)
- [`travel-agent-infrastructure/src/main/resources/travel-knowledge.cleaned.json`](travel-agent-infrastructure/src/main/resources/travel-knowledge.cleaned.json)

当前检索行为：

- Milvus 优先，本地 fallback 其次
- 支持城市别名
- 支持 topic 推断
- 支持 trip style 推断
- 支持 topic-balanced 选择
- 支持面向 planner 的 subtype 排序
- 检索来源会暴露给 planner 和前端

更多说明：

- [`docs/knowledge-rag.md`](docs/knowledge-rag.md)

相关脚本：

- [`scripts/collect_travel_attractions.py`](scripts/collect_travel_attractions.py)
- [`scripts/clean_travel_knowledge.py`](scripts/clean_travel_knowledge.py)
- [`scripts/seed-travel-knowledge.ps1`](scripts/seed-travel-knowledge.ps1)

## 安全说明

公开或推送前请确认：

- 不要提交 `.env.travel-agent`
- 不要提交真实 OpenAI / 高德 / 其他平台密钥
- 不要提交 `logs/` 下的本地运行日志
- 不要提交 `logs/release-smoke` 下生成的 smoke 日志
- 一旦密钥曾经暴露在聊天、截图或终端历史里，最好轮换

Git ignore 目前已经覆盖：

- 本地 env 文件
- 常见密钥 / 证书文件
- 运行期日志和本地产物

## 数据飞轮

这个项目现在已经具备“第一层可用的数据飞轮”：

- 每次推荐都可以记录显式用户反馈
- 反馈标签支持 `ACCEPTED`、`PARTIAL`、`REJECTED`
- 反馈会和目的地、天数、预算、agent 类型、校验结果、是否返回结构化 plan 等上下文一起保存
- 反馈数据可以通过 API 或脚本导出成数据集

导出方式：

- API：`GET /api/conversations/feedback/export?limit=200`
- 汇总 API：`GET /api/conversations/feedback/summary?limit=200`
- 脚本：[`scripts/export-feedback-dataset.ps1`](scripts/export-feedback-dataset.ps1)
- 分析脚本：[`scripts/analyze-feedback-loop.ps1`](scripts/analyze-feedback-loop.ps1)
- 默认本地导出目录：`data/exports/`

这批数据立刻可以用于：

- 离线评测
- planner 失败分析
- 检索 / 路由 / 修复策略优化
- 偏好排序或 reranker 实验

但它还不是一条完整的 RL 训练流水线，而且现阶段也不建议直接跳到强化学习。对这个项目更现实、也更高价值的顺序通常是：

1. 先收集显式反馈
2. 再分析失败模式
3. 优化路由、检索、校验、修复逻辑
4. 最后在样本量和标签质量足够时，再考虑偏好优化或 RL 风格训练

仓库里现在也补上了这条链路中间最关键的一层：

- 汇总最近一批反馈的 accept / partial / reject 比例
- 按 reason code、destination、agent type 做拆分
- 自动找出无结构化 plan、校验失败、warning 偏多、约束被放宽等高风险模式
- 导出可直接周会复盘的 markdown 报告

### TODO：偏好学习路线

- [ ] 把反馈从 `ACCEPTED` / `PARTIAL` / `REJECTED` 扩展到“用户改了什么、最终是否采用、简短分析备注”
- [ ] 建一套稳定的离线评测集，同时检查方案可用性和预算、营业时间、交通负担等硬约束
- [ ] 先做多候选方案生成，再加 reward model 或 reranker，而不是直接做端到端策略优化
- [ ] 先用反馈报告去优化路由、检索、校验、修复逻辑
- [ ] 只有在高质量标签和可信离线 benchmark 足够之后，再重新评估偏好优化或 RL

## 贡献方式

欢迎提 issue 和 pull request。

目前最值得贡献的方向：

- 真实地图数据下 planner 的稳定性
- 检索质量和排序策略
- 部署模板与生产化加固
- 前端交互和可视化体验
- 旅行知识清洗与覆盖范围

详细说明：

- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)
- [`docs/operations.md`](docs/operations.md)
- [`docs/multimodal-roadmap.md`](docs/multimodal-roadmap.md)
- [`docs/multimodal-roadmap.zh-CN.md`](docs/multimodal-roadmap.zh-CN.md)

## 许可证

本项目使用 MIT License：

- [`LICENSE`](LICENSE)
