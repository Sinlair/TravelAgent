# Travel Agent

<p align="center">
  <a href="./README.md">English</a> |
  <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Sinlair/TravelAgent/ci.yml?branch=main&label=ci&style=flat-square" />
  <img alt="GitHub Stars" src="https://img.shields.io/github/stars/Sinlair/TravelAgent?style=flat-square" />
  <img alt="GitHub Forks" src="https://img.shields.io/github/forks/Sinlair/TravelAgent?style=flat-square" />
  <img alt="GitHub Issues" src="https://img.shields.io/github/issues/Sinlair/TravelAgent?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/license-MIT-F7C948?style=flat-square" />
  <img alt="Java 21" src="https://img.shields.io/badge/java-21-ED8B00?logo=openjdk&logoColor=white&style=flat-square" />
  <img alt="Maven" src="https://img.shields.io/badge/maven-wrapper-C71A36?logo=apachemaven&logoColor=white&style=flat-square" />
  <img alt="Spring Boot 4" src="https://img.shields.io/badge/spring%20boot-4-6DB33F?logo=springboot&logoColor=white&style=flat-square" />
  <img alt="Spring AI" src="https://img.shields.io/badge/spring%20ai-2.0-0F766E?style=flat-square" />
  <img alt="WebFlux" src="https://img.shields.io/badge/webflux-reactive-0EA5E9?style=flat-square" />
  <img alt="Actuator" src="https://img.shields.io/badge/actuator-health%20checks-16A34A?style=flat-square" />
  <img alt="SSE Timeline" src="https://img.shields.io/badge/sse-timeline%20streaming-2563EB?style=flat-square" />
  <img alt="Micrometer" src="https://img.shields.io/badge/micrometer-metrics-0F766E?style=flat-square" />
  <img alt="OpenTelemetry" src="https://img.shields.io/badge/opentelemetry-tracing-7C3AED?style=flat-square" />
  <img alt="Vue 3" src="https://img.shields.io/badge/vue-3-4FC08D?logo=vuedotjs&logoColor=white&style=flat-square" />
  <img alt="TypeScript" src="https://img.shields.io/badge/typescript-5-3178C6?logo=typescript&logoColor=white&style=flat-square" />
  <img alt="Vite" src="https://img.shields.io/badge/vite-6-646CFF?logo=vite&logoColor=white&style=flat-square" />
  <img alt="Pinia" src="https://img.shields.io/badge/pinia-store-F7B93E?style=flat-square" />
  <img alt="Vitest" src="https://img.shields.io/badge/vitest-3-6E9F18?logo=vitest&logoColor=white&style=flat-square" />
  <img alt="SQLite" src="https://img.shields.io/badge/sqlite-3-003B57?logo=sqlite&logoColor=white&style=flat-square" />
  <img alt="JSON API" src="https://img.shields.io/badge/json-api-334155?style=flat-square" />
  <img alt="Docker" src="https://img.shields.io/badge/docker-ready-2496ED?logo=docker&logoColor=white&style=flat-square" />
  <img alt="Nginx" src="https://img.shields.io/badge/nginx-frontend%20proxy-009639?logo=nginx&logoColor=white&style=flat-square" />
  <img alt="PowerShell" src="https://img.shields.io/badge/powershell-automation-5391FE?logo=powershell&logoColor=white&style=flat-square" />
</p>

<p align="center">
  <img alt="Weather Agent" src="https://img.shields.io/badge/weather-agent-0EA5E9?style=flat-square" />
  <img alt="Geo Agent" src="https://img.shields.io/badge/geo-agent-2563EB?style=flat-square" />
  <img alt="Travel Planner Agent" src="https://img.shields.io/badge/travel%20planner-agent-0F766E?style=flat-square" />
  <img alt="General Agent" src="https://img.shields.io/badge/general-agent-475569?style=flat-square" />
  <img alt="Multi-Agent Routing" src="https://img.shields.io/badge/multi--agent-routing-111827?style=flat-square" />
  <img alt="Structured Planning" src="https://img.shields.io/badge/structured-planning-0F766E?style=flat-square" />
  <img alt="Amap Gaode" src="https://img.shields.io/badge/amap-gaode-1677FF?style=flat-square" />
  <img alt="Travel Knowledge RAG" src="https://img.shields.io/badge/travel%20knowledge-rag-7C3AED?style=flat-square" />
  <img alt="Image Attachments" src="https://img.shields.io/badge/image-input-enabled-C2410C?style=flat-square" />
  <img alt="Feedback Loop" src="https://img.shields.io/badge/feedback-loop-9333EA?style=flat-square" />
  <img alt="MCP Tools" src="https://img.shields.io/badge/mcp-tools-4F46E5?style=flat-square" />
  <img alt="OpenAI Compatible" src="https://img.shields.io/badge/openai-compatible-10A37F?logo=openai&logoColor=white&style=flat-square" />
  <img alt="Milvus Optional" src="https://img.shields.io/badge/milvus-optional-00A1EA?style=flat-square" />
  <img alt="Constraint Validation" src="https://img.shields.io/badge/constraint-validation-DC2626?style=flat-square" />
  <img alt="Planner Repair Loop" src="https://img.shields.io/badge/repair-loop-F59E0B?style=flat-square" />
  <img alt="Health Endpoints" src="https://img.shields.io/badge/health-endpoints-059669?style=flat-square" />
  <img alt="Feedback Export" src="https://img.shields.io/badge/feedback-export-9333EA?style=flat-square" />
  <img alt="Release Smoke" src="https://img.shields.io/badge/release-smoke-7C2D12?style=flat-square" />
</p>

<p align="center">
  Travel Agent 是一个全栈多智能体旅行规划工作台，能把自然语言需求和旅行截图转成带地图增强、可行性校验、知识检索、执行时间线以及显式反馈闭环的结构化行程。
</p>

<p align="center">
  <a href="#为什么是-travel-agent">为什么是 Travel Agent</a> •
  <a href="#功能总览">功能总览</a> •
  <a href="#多-agent-设计">多 Agent 设计</a> •
  <a href="#rag-设计">RAG 设计</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#项目结构">项目结构</a> •
  <a href="#未来增强方向">未来增强方向</a> •
  <a href="#文档">文档</a>
</p>

<p align="center">
  <img src="./docs/assets/homepage-zh-CN.png" alt="Travel Agent 首页截图" />
</p>

## 为什么是 Travel Agent

很多旅行助手只能返回一段聊天文本，这个项目更关注“可执行的旅行规划流程”：

- 不是所有请求都走一个通用 prompt，而是先路由到合适的 specialist agent。
- 不是只给一段自然语言答案，而是返回结构化行程、预算、酒店建议和约束检查。
- 不是凭空生成旅行建议，而是尽量接入高德 / Amap 的天气、POI、地理和路线信息。
- 不是把内部过程完全藏起来，而是在 UI 中暴露执行时间线和持久化状态。
- 不是只有聊天记录，而是已经具备第一层可用的推荐反馈闭环。

## 项目意义

这个项目的价值不只是“做一个会规划旅行的聊天机器人”：

- 产品价值：把模糊的聊天需求转成可查看、可修改、可执行的结构化出行方案。
- 工程价值：把多 agent 路由、共享记忆、校验、修复、RAG、运维脚本放进同一个真实仓库里，而不是只做单点 demo。
- 数据价值：已经可以记录显式推荐结果，为后续评测、失败分析和策略优化打基础。
- 交付价值：仓库从一开始就包含健康检查、Smoke、Docker、前端和脚本，不是只面向模型演示。

## 功能总览

| 模块 | 能力 |
| --- | --- |
| 多智能体工作流 | 在 `WEATHER`、`GEO`、`TRAVEL_PLANNER`、`GENERAL` 之间路由，并共享记忆和时间线 |
| 结构化规划 | 生成行程摘要、每日路线、预算拆分、酒店建议和约束检查 |
| 地图增强 | 使用高德 / Amap 做天气快照、POI 匹配、住宿片区解析和通勤增强 |
| 旅行 RAG | 从本地整理数据或 Milvus 向量检索中获取目的地知识，并附带来源和风格提示 |
| 图片输入 | 支持上传旅行图片，提取旅行事实，并在用户确认后并入正常规划链路 |
| 反馈闭环 | 记录 `ACCEPTED`、`PARTIAL`、`REJECTED`，导出数据集，并按需做聚合分析 |
| 运维能力 | 预检、启停脚本、release smoke、Docker、健康检查和 CI |

## 多 Agent 设计

这个项目采用的是“编排式多 agent”，不是多个 agent 彼此自由聊天。

### 核心角色

| 组件 | 作用 |
| --- | --- |
| `ConversationWorkflow` | 总调度器。负责接收请求、组装记忆、路由、调用 specialist、持久化结果和发布时间线。 |
| `AgentRouter` | 根据当前上下文选择本轮最合适的 agent，也可以在规划关键信息不足时提出澄清问题。 |
| `SpecialistAgent` | 执行某一个明确角色，并统一返回 `AgentExecutionResult`。 |
| `TimelinePublisher` | 把后端执行过程发布成结构化时间线事件，供前端展示。 |

### 各 agent 的角色

| Agent | 职责 |
| --- | --- |
| `WEATHER` | 处理天气、气温、下雨风险、出行和穿衣建议。 |
| `GEO` | 处理地理编码、逆地理编码、坐标、地址和地点消歧。 |
| `TRAVEL_PLANNER` | 生成结构化行程，并接上增强、校验、修复和 RAG 支撑。 |
| `GENERAL` | 处理不属于天气、地理、正式行程规划的泛旅行问题。 |

### 多 agent 怎么通信

这里的通信方式不是“agent A 直接发消息给 agent B”，而是通过统一上下文和统一结果对象来间接通信：

1. `ConversationWorkflow` 接收用户请求。
2. 它基于最近消息、任务记忆、会话摘要和长期记忆组装 `RoutingContext`。
3. `AgentRouter` 返回 `AgentRouteDecision`。
4. `ConversationWorkflow` 构造统一的 `AgentExecutionContext` 并交给选中的 `SpecialistAgent`。
5. specialist 返回统一的 `AgentExecutionResult`。
6. `ConversationWorkflow` 再把消息、任务记忆、旅行计划、长期记忆和时间线持久化。
7. `ConversationStreamHub` 通过 SSE 把时间线事件推给前端。

所以这套通信模型的特点是：

- agent 选择是中心化的
- 上下文通过 orchestrator 共享
- 输出通过统一结果对象归一化
- 前端通过持久化状态和 SSE 时间线拿到更新

### 工具调用怎么接入

系统里其实有两层通信：

- agent 和 orchestrator 之间：
  - `RoutingContext`
  - `AgentRouteDecision`
  - `AgentExecutionContext`
  - `AgentExecutionResult`
- agent 和工具之间：
  - `WEATHER`、`GEO` 可以通过 `AbstractOpenAiSpecialistAgent` 挂 MCP / tool callback
  - `TRAVEL_PLANNER` 则直接调用基础设施服务，比如 `AmapGateway`、`TravelKnowledgeRepository`、validator、repairer、enricher

对于图片辅助输入，系统会先从图片里提取旅行事实，等待用户确认或忽略，再决定是否把这些事实送回正常规划流程。

## RAG 设计

这个项目的 RAG 不是“直接 top-k 检索几段文本塞进 prompt”，而是更偏 planner 导向的检索链路。

### 数据链路

| 阶段 | 说明 |
| --- | --- |
| 种子知识 | `travel-knowledge.json` 提供一小批手工整理的初始知识。 |
| 采集 | `scripts/collect_travel_attractions.py` 从 Wikivoyage 抓取城市旅行内容。 |
| 清洗 | `scripts/clean_travel_knowledge.py` 去掉解析噪音、短垃圾片段和弱价值记录，并按 topic 做上限控制。 |
| 运行时数据 | `travel-knowledge.cleaned.json` 是当前主要的本地运行时知识库。 |

### 检索路径

运行时检索由 `RoutingTravelKnowledgeRepository` 负责：

1. 先把 destination 标准化。
2. 如果 Milvus 可用，优先走向量检索。
3. 如果向量结果为空或不可用，再回退到本地文件检索。

### 检索规划

真正关键的规划逻辑在 `TravelKnowledgeRetrievalSupport` 里。它会先构造一份 retrieval plan，包括：

- 标准化 destination
- 推断 topics
- 推断 trip styles
- 拼接后的语义查询文本
- 元数据 filter 表达式

这份 plan 会同时复用于向量检索和本地 fallback，因此两条路径在行为上尽量保持一致。

### 这条 RAG 链路为什么更重要

当前实现已经不是纯文本检索，而是强调：

- destination 作为强约束或近似硬约束
- 根据用户问题和偏好一起推断 topic
- 推断 `relaxed`、`family`、`foodie`、`museum` 等 trip style
- 不是简单 top-k，而是做 topic-balanced selection
- 对 hotel 和 transit 做 subtype 感知排序
- 把来源和检索元数据返回给前端

### RAG 最终怎么进入 planner

`TravelPlannerAgent` 会在草稿计划生成后、最终回答拼装前做目的地知识检索。最终 planner 会把下面几类信息一起整合：

- 天气快照
- 本地知识检索结果
- Amap 增强结果
- 校验与修复结果

最后再输出结构化 travel plan 和最终回答。

## 架构

| 模块 | 职责 |
| --- | --- |
| `travel-agent-types` | 通用 API 响应和异常类型 |
| `travel-agent-domain` | 领域实体、值对象、仓储接口和服务契约 |
| `travel-agent-amap` | Amap HTTP 网关与配置 |
| `travel-agent-infrastructure` | Spring AI agents、检索、持久化适配器和向量集成 |
| `travel-agent-app` | WebFlux API、健康检查、SSE 时间线和启动逻辑 |
| `travel-agent-amap-mcp-server` | 面向 Amap 的独立 MCP Server |
| `web` | Vue 3 前端工作台 |

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4、Spring WebFlux、Spring Validation、Spring Boot Actuator |
| AI | Spring AI、OpenAI 兼容聊天接口、MCP |
| 存储 | SQLite、可选 Milvus |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vitest |
| 运维 | PowerShell 脚本、Docker、Docker Compose、Nginx、GitHub Actions |

## 快速开始

### 运行前准备

- Java 21
- Node.js 和 npm
- 如果要跑 Milvus 或容器化部署，需要 Docker Desktop
- 如果要重跑数据采集和清洗，需要 Python 3

### 1. 创建本地环境文件

```powershell
Copy-Item .env.travel-agent.example .env.travel-agent
```

### 2. 跑预检

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight-travel-agent.ps1
```

### 3. 启动整套应用

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-travel-agent.ps1 -Build -StartFrontend -RunPreflight -ToolProvider LOCAL
```

默认本地地址：

- 后端：`http://localhost:18080`
- 前端：`http://localhost:4173`

### 4. 停止

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-travel-agent.ps1
```

## 配置

重点环境变量：

| 类别 | 变量 |
| --- | --- |
| OpenAI / 兼容网关 | `SPRING_AI_OPENAI_API_KEY`、`SPRING_AI_OPENAI_BASE_URL`、`SPRING_AI_OPENAI_CHAT_MODEL`、`SPRING_AI_OPENAI_EMBEDDING_MODEL` |
| 工具提供方式 | `TRAVEL_AGENT_TOOL_PROVIDER`、`TRAVEL_AGENT_AMAP_MCP_ENABLED`、`TRAVEL_AGENT_AMAP_API_KEY`、`TRAVEL_AGENT_AMAP_REQUESTS_PER_SECOND` |
| 检索 | `TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED`、`TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI`、`TRAVEL_AGENT_KNOWLEDGE_VECTOR_COLLECTION_NAME`、`TRAVEL_AGENT_MILVUS_ENABLED`、`TRAVEL_AGENT_MILVUS_URI` |
| 前端地图渲染 | `VITE_AMAP_WEB_KEY`、`VITE_AMAP_SECURITY_JS_CODE` |
| 部署 profile | `SPRING_PROFILES_ACTIVE=prod` |

说明：

- `SPRING_AI_OPENAI_BASE_URL` 用于接 OpenAI 兼容网关。
- `TRAVEL_AGENT_TOOL_PROVIDER` 支持 `LOCAL` 和 `MCP`。
- `.env.travel-agent` 已被 Git 忽略，不应提交。

## 开发方式

### 后端

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "<your-openai-key>"
.\mvnw.cmd -pl travel-agent-app -am spring-boot:run
```

### 前端

```powershell
Set-Location .\web
npm.cmd ci
npm.cmd run dev
```

### 独立 MCP Server

```powershell
$env:TRAVEL_AGENT_AMAP_API_KEY = "<your-amap-web-service-key>"
.\mvnw.cmd -pl travel-agent-amap-mcp-server -am spring-boot:run
```

## 常用命令

| 任务 | 命令 |
| --- | --- |
| 后端测试 | `.\mvnw.cmd test` |
| 前端测试 | `Set-Location .\web; npm.cmd run test` |
| 前端构建 | `Set-Location .\web; npm.cmd run build` |
| Release smoke | `powershell -ExecutionPolicy Bypass -File .\scripts\release-smoke-travel-agent.ps1` |
| 导出反馈数据集 | `powershell -ExecutionPolicy Bypass -File .\scripts\export-feedback-dataset.ps1` |
| 分析反馈闭环 | `powershell -ExecutionPolicy Bypass -File .\scripts\analyze-feedback-loop.ps1` |

## Docker

仓库已包含：

- [`Dockerfile.app`](./Dockerfile.app)
- [`Dockerfile.mcp`](./Dockerfile.mcp)
- [`docker-compose.app.yml`](./docker-compose.app.yml)
- [`docker-compose.milvus.yml`](./docker-compose.milvus.yml)
- [`web/Dockerfile`](./web/Dockerfile)
- [`web/nginx.conf`](./web/nginx.conf)

启动应用栈：

```powershell
docker compose -f docker-compose.app.yml up --build -d
```

启用 MCP profile：

```powershell
docker compose -f docker-compose.app.yml --profile mcp up --build -d
```

启用向量检索时，单独拉起 Milvus：

```powershell
docker compose -f docker-compose.milvus.yml up -d
```

## 项目结构

仓库是按职责拆分的，不只是按前后端分一下目录。

| 目录 | 作用 |
| --- | --- |
| `travel-agent-app/` | 应用层：REST API、SSE 时间线、健康检查、DTO、启动逻辑和主工作流。 |
| `travel-agent-domain/` | 核心领域契约：实体、值对象、仓储接口、路由上下文、执行上下文和服务接口。 |
| `travel-agent-infrastructure/` | 具体实现：LLM agents、检索、SQLite 持久化、Milvus 集成、校验器、修复器和 Amap 适配。 |
| `travel-agent-amap/` | 主应用使用的 Amap HTTP 网关模块。 |
| `travel-agent-amap-mcp-server/` | 把 Amap 能力暴露成 MCP 工具的独立服务。 |
| `travel-agent-types/` | 公用响应码、响应包装和异常类型。 |
| `web/` | Vue 前端工作台，包括聊天区、方案区、时间线和反馈闭环面板。 |
| `scripts/` | 采集、清洗、启动、Smoke、导出和运维脚本。 |
| `docs/` | 说明文档、release checklist、RAG 文档、多模态文档和 README 截图资源。 |

```text
.
|- travel-agent-app
|- travel-agent-domain
|- travel-agent-infrastructure
|- travel-agent-amap
|- travel-agent-amap-mcp-server
|- travel-agent-types
|- web
|- scripts
`- docs
```

## 文档

- [`docs/knowledge-rag.md`](./docs/knowledge-rag.md)
- [`docs/multimodal-roadmap.md`](./docs/multimodal-roadmap.md)
- [`docs/multimodal-roadmap.zh-CN.md`](./docs/multimodal-roadmap.zh-CN.md)
- [`docs/operations.md`](./docs/operations.md)
- [`docs/release-checklist.md`](./docs/release-checklist.md)
- [`CONTRIBUTING.md`](./CONTRIBUTING.md)
- [`SECURITY.md`](./SECURITY.md)

## 当前限制

- 现阶段最强路径仍然依赖 Amap grounding，所以更适合中国旅行场景。
- 部分检索片段仍偏列表化，还不够像理想的 planner 指导信息。
- 当前生产部署材料已经可用，但 secrets、反向代理和 TLS 模板还比较轻量。
- 规划效果高度依赖模型提供方和地图提供方配置是否完整。

## 未来增强方向

后面真正值得加强的点，不是简单“多写几个 prompt”，而是这些结构性能力：

- 更强的 RAG schema：
  - 把 hotel 和 transit 进一步拆成更适合 planner 的结构化知识
  - 增强城市别名和偏好归一化能力
- 更稳定的 planner 评测：
  - 建立同时覆盖“有用性”和“硬约束”的离线 benchmark
  - 系统比较 accepted / partial / rejected 的差异
- 更明确的多 agent 协作策略：
  - 设计 planner、weather、geo 之间更清晰的 handoff 规则
  - 决定哪些子步骤值得并行或显式暴露
- 更强的多模态输入：
  - 提升截图里的结构化事实提取质量
  - 只在 vision-first 不够稳定的场景下再补 OCR fallback
- 更强的生产化：
  - 完善 secrets 管理模板
  - 补齐反向代理和 TLS 部署范例
  - 补更多 observability dashboard 和告警接入

## 许可证

本项目采用 MIT License，见 [`LICENSE`](./LICENSE)。
