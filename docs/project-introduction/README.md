# TravelAgent 项目介绍文档集

## 这套文档是干什么的

这是一组面向中文读者的项目介绍文档。
它的目标不是替代根目录 `README`，而是把“这个项目到底是什么、做了什么、用了哪些能力、采用了哪些设计方法”拆开讲清楚。

如果你只想快速启动项目，请优先阅读：

- [`../../README.md`](../../README.md)
- [`../../README.zh-CN.md`](../../README.zh-CN.md)

如果你想真正理解这个项目，请从这套文档开始。

## 阅读顺序

### 适合第一次了解项目的人

1. [`01-project-positioning.md`](./01-project-positioning.md)
2. [`02-core-capabilities.md`](./02-core-capabilities.md)
3. [`03-skills-and-techniques.md`](./03-skills-and-techniques.md)

### 适合想看架构和方法的人

1. [`04-design-methods.md`](./04-design-methods.md)
2. [`05-architecture-and-modules.md`](./05-architecture-and-modules.md)
3. [`06-request-lifecycle.md`](./06-request-lifecycle.md)

### 适合想看产品化和工程化能力的人

1. [`07-productization-and-quality.md`](./07-productization-and-quality.md)
2. [`../herness-contract.md`](../herness-contract.md)
3. [`../system-architecture.md`](../system-architecture.md)

## 文档结构

### 1. 项目定位

文件：
[`01-project-positioning.md`](./01-project-positioning.md)

说明：

- 项目是什么
- 为什么要做它
- 解决什么问题
- 目标用户是谁
- 它和普通聊天机器人、OTA、地图工具有什么区别

### 2. 核心能力

文件：
[`02-core-capabilities.md`](./02-core-capabilities.md)

说明：

- 多智能体路由
- 结构化旅行规划
- 图片辅助输入
- Amap 增强
- RAG 检索
- 校验与修复
- 局部重规划
- 版本、反馈、质量运营

### 3. 技能与实现技巧

文件：
[`03-skills-and-techniques.md`](./03-skills-and-techniques.md)

说明：

- 项目用了哪些系统能力
- 每种能力是怎么工作的
- 用了哪些技术技巧把能力串起来

### 4. 设计方法

文件：
[`04-design-methods.md`](./04-design-methods.md)

说明：

- DDD
- ports-and-adapters
- 编排式多智能体
- 显式 planner pipeline
- 契约优先
- 可观测性优先
- 降级与回退设计

### 5. 模块与架构

文件：
[`05-architecture-and-modules.md`](./05-architecture-and-modules.md)

说明：

- 各模块职责
- 关键对象和关键类
- 数据和状态怎么流动
- 配置开关怎么影响运行路径

### 6. 一次请求如何走完

文件：
[`06-request-lifecycle.md`](./06-request-lifecycle.md)

说明：

- 普通文本规划请求
- 图片辅助请求
- 局部重规划请求
- 反馈提交请求
- SSE 时间线与持久化

### 7. 产品化与质量能力

文件：
[`07-productization-and-quality.md`](./07-productization-and-quality.md)

说明：

- 从 MVP 到 beta 的产品化能力
- 本地 demo 启动链路
- 质量场景回放
- 反馈运营面板
- 当前边界与后续方向

## 对应关系

如果你之前看过仓库里的这些文档，可以按下面方式建立映射：

- 想知道“系统长什么样”：看 [`../system-architecture.md`](../system-architecture.md)
- 想知道“统一结果契约是什么”：看 [`../herness-contract.md`](../herness-contract.md)
- 想知道“RAG 怎么做”：看 [`../knowledge-rag.md`](../knowledge-rag.md) 和 [`../rag-feature-guide.md`](../rag-feature-guide.md)
- 想知道“这项目整体上值不值得读”：先看 [`01-project-positioning.md`](./01-project-positioning.md)

## 建议用法

如果你要向别人介绍这个项目，不建议只丢仓库链接。
更好的方式是：

1. 先给他看这篇索引
2. 再按角色给他对应文档
3. 最后再看源码和架构图

这样理解成本会低很多。
