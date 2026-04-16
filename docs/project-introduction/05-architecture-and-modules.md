# 05. 架构与模块

## 总体架构一句话

TravelAgent 的总体架构可以概括为：

`DDD 分层 + ports-and-adapters + 编排式多智能体 + 显式规划流水线 + 工作台前端`

它不是“单个 agent 应用”，也不是“单一 RAG 服务”，而是一个由多个模块共同组成的产品系统。

## 模块全景

仓库主要由以下模块组成：

- `travel-agent-domain`
- `travel-agent-app`
- `travel-agent-infrastructure`
- `travel-agent-amap`
- `travel-agent-amap-mcp-server`
- `travel-agent-types`
- `web`

每个模块都有明确职责。

## 1. travel-agent-domain

### 角色

这是领域层，负责表达业务语义。

### 主要内容

- 实体
- 值对象
- 端口接口
- 仓储接口
- 领域服务契约

### 为什么它重要

如果没有这一层，旅行规划里的核心概念就会散落在各种 service 和 controller 中。
有了它，业务核心就有了稳定落点。

### 典型对象

从当前项目结构看，领域层承载了很多关键对象，例如：

- `TaskMemory`
- `TravelPlan`
- `TravelPlanDay`
- `TravelChecklistItem`
- `TravelPlanVersionSnapshot`
- `TimelineEvent`

这些对象不是“为了数据库建模”，而是为了表达系统真正关心的业务状态。

## 2. travel-agent-app

### 角色

这是应用层，负责把一次用户请求编排成完整用例。

### 主要内容

- 工作流编排
- REST API
- SSE 输出
- 应用服务
- 健康检查
- 启动配置

### 核心位置

这个模块最关键的中心点是 `ConversationWorkflow`。
它负责把一次 `/api/conversations/chat` 请求从入口推进到最终结果。

### 它为什么重要

如果说领域层是“业务词典”，那应用层就是“业务导演”。
它不直接做所有细节，但负责组织全流程。

## 3. travel-agent-infrastructure

### 角色

这是基础设施层，负责具体实现。

### 主要内容

- LLM agent
- 路由器实现
- 任务记忆提取
- 图片解释
- 检索与排序
- 校验与修复
- 仓储实现
- 工具适配

### 它为什么重要

这一层是“能力工厂”。
真正的外部调用、技术实现和适配逻辑，大多都落在这里。

### 典型职责

- 路由哪个 specialist
- 生成规划草案
- 用地图能力做增强
- 检索知识
- 校验和修复计划
- 把会话和反馈存进 SQLite

## 4. travel-agent-amap

### 角色

这是独立的高德/Amap 接入模块。

### 为什么要单独拆出来

地图能力在这个项目里非常重要，但它本质上是外部能力。
单独拆出来的价值是：

- 边界更清晰
- 替换更容易
- 不把地图细节散进业务模块

## 5. travel-agent-amap-mcp-server

### 角色

这是把 Amap 能力以 MCP 工具方式暴露出去的独立服务。

### 为什么要有它

项目支持两条工具路径：

- `LOCAL`
- `MCP`

MCP 路径需要一个独立工具服务器，这个模块就是为此存在。

### 它的价值

- 让工具提供方式可切换
- 让核心系统不和单一工具调用方式绑死

## 6. travel-agent-types

### 角色

这个模块负责共享的类型定义和通用响应包装。

### 为什么要有它

这样可以避免多个模块重复定义相同响应语义。

## 7. web

### 角色

这是前端工作台。

### 它不是简单聊天框

它包含的内容明显超出了普通 chat UI：

- 会话历史
- 聊天输入
- 结构化行程面板
- 时间线
- 反馈面板
- 版本差异
- checklist 面板
- 运营过滤视图

这决定了项目整体是一个 workspace，而不是一个“对话页面”。

## 模块之间如何协作

可以把它理解成下面这条链：

1. `web` 发起请求
2. `travel-agent-app` 编排流程
3. `travel-agent-domain` 提供业务模型和接口
4. `travel-agent-infrastructure` 提供具体实现
5. `travel-agent-amap` / `travel-agent-amap-mcp-server` 提供地图和工具能力
6. 结果返回到 `web` 做统一呈现

## 关键运行对象

## 1. ConversationWorkflow

它是对话主入口，也是最关键的编排者。

典型职责包括：

- 处理文本输入
- 处理图片输入与确认/忽略
- 重建任务记忆
- 调用路由器
- 调用 specialist
- 收敛最终响应
- 持久化状态

## 2. AgentRouter

它负责决定当前请求走哪条 specialist 路径。

这是系统“第一层分流器”。

## 3. SpecialistAgent

specialist 是执行者。
它们不是互相自由聊天，而是在应用层编排下完成自己的职责。

## 4. TravelPlannerAgent

这是最复杂的一条路径执行器。

它负责：

- 生成草案
- 增强
- 校验
- 修复
- 输出结构化计划

## 5. Repository Adapter

包括：

- 会话持久化
- 反馈持久化
- 计划版本持久化
- 时间线持久化

这些适配器让工作台能力可以跨刷新、跨会话保留。

## 6. Timeline Publisher / Stream Hub

这一部分把过程事件推回前端，让用户和开发者都能看到执行轨迹。

## 数据是怎么流的

## 1. 输入数据

输入可能来自：

- 文本
- 图片
- 图片确认/忽略操作
- 结构化 brief
- 局部重规划指令

## 2. 中间状态

中间状态主要包括：

- 任务记忆
- 路由结果
- specialist 执行结果
- travel plan 草案
- 增强结果
- 校验结果
- 修复结果
- timeline 事件

## 3. 输出数据

统一输出包括：

- `agentType`
- `answer`
- `taskMemory`
- `travelPlan`
- `timeline`
- `feedbackTarget`
- `issues`
- `missingInformation`
- `constraintSummary`

这套结构支撑了整个前端工作台。

## 配置开关如何影响运行

这个项目不是只有一条固定运行路径，而是会根据配置走不同能力分支。

### 1. tool provider

- `LOCAL`
- `MCP`

作用：

- 决定地图和工具能力通过本地适配器还是 MCP 服务接入

### 2. memory provider

- `AUTO`
- `SQLITE`
- `MILVUS`

作用：

- 决定长期记忆使用哪种存储路径

### 3. knowledge vector enable

作用：

- 决定知识检索是否启用向量增强

### 4. local-demo profile

作用：

- 决定是否使用本地 stub/mocked 启动链路

## 为什么这个架构值得看

这个架构的价值不在“模块多”，而在“模块之间关系清楚”。

它让几个本来容易搅在一起的问题被拆开了：

- 业务语义和外部依赖分开
- 编排和执行分开
- 结果契约和 UI 渲染分开
- 地图能力和 planner 本体分开
- 本地开发和线上模式分开

这使项目有能力在持续加功能的同时，还保持一定的结构稳定性。

## 相关文档

- [`../system-architecture.md`](../system-architecture.md)
- [`../herness-contract.md`](../herness-contract.md)
- [`../design-patterns.md`](../design-patterns.md)
