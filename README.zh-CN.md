# Travel Agent

<p align="center">
  <a href="./README.md">English</a> |
  <a href="./README.zh-CN.md">简体中文</a>
</p>

<p align="center">
  <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Sinlair/TravelAgent/ci.yml?branch=main&label=ci&style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/license-MIT-F7C948?style=flat-square" />
  <img alt="Java 21" src="https://img.shields.io/badge/java-21-ED8B00?logo=openjdk&logoColor=white&style=flat-square" />
  <img alt="Spring Boot 4" src="https://img.shields.io/badge/spring%20boot-4-6DB33F?logo=springboot&logoColor=white&style=flat-square" />
  <img alt="Spring AI" src="https://img.shields.io/badge/spring%20ai-2.0-0F766E?style=flat-square" />
  <img alt="WebFlux" src="https://img.shields.io/badge/webflux-reactive-0EA5E9?style=flat-square" />
  <img alt="Vue 3" src="https://img.shields.io/badge/vue-3-4FC08D?logo=vuedotjs&logoColor=white&style=flat-square" />
  <img alt="TypeScript" src="https://img.shields.io/badge/typescript-5-3178C6?logo=typescript&logoColor=white&style=flat-square" />
  <img alt="Vite" src="https://img.shields.io/badge/vite-6-646CFF?logo=vite&logoColor=white&style=flat-square" />
  <img alt="Vitest" src="https://img.shields.io/badge/vitest-3-6E9F18?logo=vitest&logoColor=white&style=flat-square" />
  <img alt="SQLite" src="https://img.shields.io/badge/sqlite-3-003B57?logo=sqlite&logoColor=white&style=flat-square" />
  <img alt="Docker" src="https://img.shields.io/badge/docker-ready-2496ED?logo=docker&logoColor=white&style=flat-square" />
</p>

<p align="center">
  <img alt="Multi-Agent Routing" src="https://img.shields.io/badge/multi--agent-routing-111827?style=flat-square" />
  <img alt="Structured Planning" src="https://img.shields.io/badge/structured-planning-0F766E?style=flat-square" />
  <img alt="Amap Gaode" src="https://img.shields.io/badge/amap-gaode-1677FF?style=flat-square" />
  <img alt="Travel Knowledge RAG" src="https://img.shields.io/badge/travel%20knowledge-rag-7C3AED?style=flat-square" />
  <img alt="Image Attachments" src="https://img.shields.io/badge/image-input-enabled-C2410C?style=flat-square" />
  <img alt="Feedback Loop" src="https://img.shields.io/badge/feedback-loop-9333EA?style=flat-square" />
  <img alt="MCP Tools" src="https://img.shields.io/badge/mcp-tools-4F46E5?style=flat-square" />
  <img alt="OpenAI Compatible" src="https://img.shields.io/badge/openai-compatible-10A37F?logo=openai&logoColor=white&style=flat-square" />
  <img alt="Milvus Optional" src="https://img.shields.io/badge/milvus-optional-00A1EA?style=flat-square" />
</p>

<p align="center">
  Travel Agent 是一个全栈多智能体旅行规划工作台，能把自然语言需求和旅行截图转成带地图增强、可行性校验、知识检索、执行时间线以及显式反馈闭环的结构化行程。
</p>

<p align="center">
  <a href="#为什么是-travel-agent">为什么是 Travel Agent</a> •
  <a href="#功能总览">功能总览</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#开发方式">开发方式</a> •
  <a href="#文档">文档</a>
</p>

<p align="center">
  <img src="./docs/assets/homepage.png" alt="Travel Agent 首页截图" />
</p>

## 为什么是 Travel Agent

很多旅行助手只能返回一段聊天文本，这个项目更关注“可执行的旅行规划流程”：

- 不是所有请求都走一个通用 prompt，而是先路由到合适的 specialist agent。
- 不是只给一段自然语言答案，而是返回结构化行程、预算、酒店建议和约束检查。
- 不是凭空生成旅行建议，而是尽量接入高德 / Amap 的天气、POI、地理和路线信息。
- 不是把内部过程完全藏起来，而是在 UI 中暴露执行时间线和持久化状态。
- 不是只有聊天记录，而是已经具备第一层可用的推荐反馈闭环。

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

## 这个仓库和普通 Demo 的区别

| 常见做法 | Travel Agent 的做法 |
| --- | --- |
| 只有聊天式行程文本 | 返回 UI 可直接消费的结构化计划 |
| 缺少外部信息 grounding | 尽量接入地图和天气信息增强 |
| 编排逻辑全在黑盒里 | 前端可见执行时间线和持久化状态 |
| 一次生成就结束 | 带校验和修复流程 |
| 没有产品反馈 | 可以记录推荐结果并做汇总分析 |

## 系统流程

1. `ConversationWorkflow` 组装当前消息、最近历史、任务记忆、摘要和长期记忆。
2. `AgentRouter` 为本轮请求选择最合适的 specialist。
3. 对应 specialist 在共享上下文中执行。
4. 行程规划请求会经历生成、增强、校验、修复和持久化。
5. 前端拿到回答、任务记忆、时间线事件和结构化 `travelPlan`。

对于图片辅助输入，系统会先从图片里提取旅行事实，等待用户确认或忽略，再决定是否把这些事实送回正常规划流程。

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

## 许可证

本项目采用 MIT License，见 [`LICENSE`](./LICENSE)。
