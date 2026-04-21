# TravelAgent 项目总览

> 🤖 基于 AI 的智能旅行规划助手

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-green.svg)](https://vuejs.org/)

---

## 📋 目录

- [项目简介](#项目简介)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [架构设计](#架构设计)
- [快速开始](#快速开始)
- [文档导航](#文档导航)
- [项目状态](#项目状态)
- [路线图](#路线图)
- [贡献指南](#贡献指南)

---

## 项目简介

TravelAgent 是一个基于 DDD (领域驱动设计) 架构的智能旅行规划系统，结合了先进的大语言模型 (LLM) 和 RAG (检索增强生成) 技术，为用户提供：

- 🎯 **个性化推荐**：基于用户偏好的智能旅行推荐
- 💬 **自然对话**：通过对话式交互规划旅行
- 📚 **知识增强**：内置丰富的旅行知识库
- 🗺️ **地图集成**：高德地图深度集成
- 📊 **结构化输出**：生成详细的旅行行程

### 项目亮点

| 特性 | 描述 | 优势 |
|------|------|------|
| RAG 知识库 | 5层检索优化 pipeline | 检索准确率 85%+ |
| DDD 架构 | 清晰的领域模型设计 | 可维护性高 |
| 多智能体 | 专业的 Agent 协作 | 决策更智能 |
| 实时通信 | SSE 流式响应 | 用户体验流畅 |
| 多模态 | 支持文本/图片/位置 | 交互方式丰富 |

---

## 核心功能

### 1. 智能对话规划

```
用户: "我想去北京玩3天，喜欢历史文化"
  ↓
AI: 理解意图 → 检索知识 → 生成行程 → 展示结果
  ↓
行程:
  Day 1: 故宫 → 天安门 → 王府井
  Day 2: 长城 → 颐和园
  Day 3: 天坛 → 798艺术区
```

### 2. RAG 知识库检索

完整的 5 层检索 Pipeline：

```
[1] 查询理解 → 拼写纠错/查询扩展/意图识别
  ↓
[2] 元数据增强 → 11个字段自动推断
  ↓
[3] 智能分块 → 4种分块策略
  ↓
[4] 混合检索 → BM25(40%) + 向量(60%)
  ↓
[5] 重排序 → 4维评分 + MMR多样性
```

**性能指标**：
- 检索准确率：~85% (提升 42%)
- 个性化匹配：4 维度评分
- 查询理解：5 步处理流程
- 结果多样性：MMR 算法保证

### 3. 地图集成 (高德地图)

- 📍 POI 搜索和推荐
- 🗺️ 路线规划和导航
- 📊 地理数据可视化
- 🔍 周边兴趣点发现

### 4. 行程生成

```json
{
  "destination": "beijing",
  "duration": 3,
  "dailyPlans": [
    {
      "day": 1,
      "date": "2026-05-01",
      "spots": [
        {
          "name": "故宫",
          "arrivalTime": "09:00",
          "departureTime": "12:00",
          "tips": "建议提前网上购票",
          "knowledgeContext": "..."
        }
      ]
    }
  ]
}
```

---

## 技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 主要编程语言 |
| Spring Boot | 4 | 应用框架 |
| Spring AI | 2.0 | AI/LLM 集成 |
| Maven | 3.9+ | 构建工具 |
| Milvus | 2.4+ | 向量数据库 |
| H2 / PostgreSQL | - | 关系数据库 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3 | 前端框架 |
| TypeScript | 5+ | 类型安全 |
| Vite | 5+ | 构建工具 |
| Element Plus | - | UI 组件库 |
| Markmap | - | 思维导图 |

### 基础设施

| 服务 | 用途 |
|------|------|
| Docker | 容器化 |
| Nginx | 反向代理 |
| Prometheus | 监控 |
| Grafana | 可视化 |

---

## 架构设计

### DDD 分层架构

```
┌─────────────────────────────────────────┐
│              Web 前端 (Vue 3)            │
└─────────────────────────────────────────┘
                  ↓ HTTP
┌─────────────────────────────────────────┐
│         应用层 (travel-agent-app)        │
│  - ConversationWorkflow (对话编排)       │
│  - ItineraryPlanWorkflow (行程编排)     │
│  - REST Controllers (API 接口)          │
└─────────────────────────────────────────┘
                  ↓ 调用
┌─────────────────────────────────────────┐
│         领域层 (travel-agent-domain)     │
│  - 领域模型 (Entity/Value Object)        │
│  - 领域服务                             │
│  - 领域事件                             │
└─────────────────────────────────────────┘
                  ↓ 依赖
┌─────────────────────────────────────────┐
│  基础设施层 (travel-agent-infrastructure)│
│  - 仓储实现 (Repository)                 │
│  - RAG 服务 (检索/重排序/分块)          │
│  - 外部服务集成 (高德地图/OpenAI)       │
└─────────────────────────────────────────┘
```

### 核心模块

| 模块 | 职责 | 关键组件 |
|------|------|---------|
| travel-agent-domain | 核心业务逻辑 | 领域模型、值对象 |
| travel-agent-app | 用例编排 | 工作流、控制器 |
| travel-agent-infrastructure | 技术实现 | RAG服务、仓储 |
| travel-agent-amap | 地图集成 | POI搜索、路线规划 |
| travel-agent-types | 类型定义 | DTO、枚举 |

---

## 快速开始

### 环境要求

- Java 21+
- Maven 3.9+
- Node.js 18+
- Docker 24+ (可选)

### 5 分钟启动

```bash
# 1. 克隆项目
git clone https://github.com/TaoT5/travel-agent.git
cd travel-agent

# 2. 配置环境
cp .env.travel-agent.example .env.travel-agent
# 编辑 .env.travel-agent 填写 API Key

# 3. 启动服务
# 方式 1: 本地开发
./mvnw spring-boot:run -pl travel-agent-app  # 后端
cd web && npm install && npm run dev  # 前端

# 方式 2: Docker (推荐)
docker compose -f docker-compose.milvus.yml up -d
docker compose -f docker-compose.app.yml up -d

# 4. 访问应用
# 前端: http://localhost:5173 (本地) 或 http://localhost:80 (Docker)
# 后端: http://localhost:8080
```

详细部署说明请参考 [部署指南](docs/deployment-guide.md)

---

## 文档导航

### 📚 完整文档列表

| 文档 | 描述 | 链接 |
|------|------|------|
| **README** | 项目介绍和快速开始 | [README.md](README.md) |
| **RAG 功能文档** | RAG 知识库详细说明 | [查看](docs/rag-feature-guide.md) |
| **开发指南** | 环境搭建、代码规范、测试 | [查看](docs/development-guide.md) |
| **部署指南** | 本地/Docker/生产环境部署 | [查看](docs/deployment-guide.md) |
| **系统架构** | 架构设计和技术选型 | [查看](docs/system-architecture.md) |
| **设计模式** | 项目中使用的设计模式 | [查看](docs/design-patterns.md) |
| **API 文档** | REST API 接口说明 | [Swagger UI](http://localhost:8080/swagger-ui.html) |
| **TODO** | 项目改进计划和进度 | [查看](TODO.md) |
| **贡献指南** | 如何贡献代码 | [查看](CONTRIBUTING.md) |

### 🎯 按角色推荐阅读

#### 新用户
1. [README.md](README.md) - 了解项目
2. [快速开始](#快速开始) - 运行起来
3. [RAG 功能文档](docs/rag-feature-guide.md) - 核心功能

#### 开发者
1. [开发指南](docs/development-guide.md) - 环境搭建
2. [系统架构](docs/system-architecture.md) - 理解架构
3. [TODO.md](TODO.md) - 了解计划
4. [设计模式](docs/design-patterns.md) - 学习代码结构

#### 运维人员
1. [部署指南](docs/deployment-guide.md) - 部署应用
2. [运维手册](docs/operations.md) - 日常运维
3. [监控配置](docs/deployment-guide.md#监控与运维) - 监控告警

#### 贡献者
1. [贡献指南](CONTRIBUTING.md) - 了解流程
2. [代码规范](docs/development-guide.md#代码规范) - 编码要求
3. [TODO.md](TODO.md) - 寻找任务

---

## 项目状态

### ✅ 已完成功能 (Phase 1)

- [x] 基础对话系统
- [x] 行程规划引擎
- [x] 高德地图集成
- [x] RAG 知识库
  - [x] 查询理解增强 (5步流程)
  - [x] 元数据增强 (11个字段+8个推断)
  - [x] 混合检索 (BM25+向量)
  - [x] 智能分块 (4种策略)
  - [x] Reranking重排序 (4维评分+MMR)
- [x] 前端 UI (Vue 3)
- [x] Docker 部署支持

### 🚧 开发中 (Phase 2 - 规划中)

- [ ] 用户画像和个性化推荐
- [ ] 实时数据集成 (天气/拥挤度)
- [ ] 行程协作功能
- [ ] 特殊场景支持 (亲子/户外等)

### 📊 代码统计

```
总代码行数: ~50,000 行
├─ 后端 Java: ~30,000 行
├─ 前端 TypeScript: ~15,000 行
└─ 测试代码: ~5,000 行

文件数量: ~200 个
模块数量: 7 个
测试用例: 30+ 个
```

### 📈 性能指标

| 指标 | 数值 | 说明 |
|------|------|------|
| 检索准确率 | ~85% | RAG Phase 1 优化后 |
| 响应时间 | <2s | 平均对话响应 |
| 并发用户 | 100+ | 单机支持 |
| 测试覆盖率 | 60%+ | 核心功能 |

---

## 路线图

### Phase 1: RAG 优化 ✅ (已完成)

**时间**: 2026年4月  
**目标**: 提升检索质量和个性化推荐

- ✅ 查询理解增强
- ✅ 元数据自动推断
- ✅ 混合检索实现
- ✅ 智能文档分块
- ✅ Reranking 重排序

**成果**: 检索准确率从 60% 提升到 85%

### Phase 2: 个性化与协作 🚧 (规划中)

**时间**: 2026年5-6月  
**目标**: 增强用户体验和协作能力

- [ ] 用户画像系统
- [ ] 个性化推荐算法
- [ ] 实时数据集成
  - 天气 API
  - 拥挤度数据
  - 事件信息
- [ ] 行程协作
  - 多人编辑
  - 评论系统
  - 版本历史
- [ ] 特殊场景支持
  - 亲子游
  - 户外探险
  - 美食之旅

### Phase 3: 多模态与国际化 📅 (计划中)

**时间**: 2026年7-9月  
**目标**: 扩展交互方式和用户群体

- [ ] 多模态交互
  - 图片理解
  - 语音输入
  - 视频推荐
- [ ] 国际化支持
  - 多语言界面
  - 多语言知识库
  - 地区适配
- [ ] 性能优化
  - 缓存策略
  - 负载均衡
  - CDN 加速

### Phase 4: 生态建设 📅 (远期)

**时间**: 2026年10月+  
**目标**: 构建旅行规划生态

- [ ] 开放 API
- [ ] 第三方集成
- [ ] 插件系统
- [ ] 社区功能

详细路线图请参考 [TODO.md](TODO.md)

---

## 贡献指南

我们欢迎所有形式的贡献！🎉

### 贡献方式

1. **报告 Bug**
   - 在 Issues 中描述问题
   - 提供复现步骤
   - 附加截图或日志

2. **提出新功能**
   - 说明功能场景
   - 描述预期效果
   - 提供示例

3. **提交代码**
   - Fork 项目
   - 创建特性分支
   - 编写测试
   - 提交 Pull Request

4. **改进文档**
   - 修正错误
   - 补充说明
   - 添加示例

### 快速开始

```bash
# 1. Fork 并克隆项目
git clone https://github.com/YOUR_USERNAME/travel-agent.git

# 2. 创建特性分支
git checkout -b feature/amazing-feature

# 3. 开发并测试
# ... 编写代码 ...
./mvnw test

# 4. 提交代码
git commit -m "feat: add amazing feature"
git push origin feature/amazing-feature

# 5. 创建 Pull Request
```

详细贡献流程请参考 [贡献指南](CONTRIBUTING.md)

### 代码规范

- 遵循 [Alibaba Java Coding Guidelines](https://github.com/alibaba/p3c)
- 使用 Conventional Commits 规范
- 编写单元测试 (目标覆盖率 80%+)
- 更新相关文档

### 致谢

感谢所有贡献者的辛勤付出！❤️

---

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 联系方式

- 📧 Email: [taotao5@example.com](mailto:taotao5@example.com)
- 🐛 Issues: [GitHub Issues](https://github.com/TaoT5/travel-agent/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/TaoT5/travel-agent/discussions)

---

## Star History

如果这个项目对你有帮助，请给我们一个 ⭐ Star！

[![Star History Chart](https://api.star-history.com/svg?repos=TaoT5/travel-agent&type=Date)](https://star-history.com/#TaoT5/travel-agent&Date)

---

**项目版本**: v0.1.0  
**最后更新**: 2026-04-11  
**维护者**: [TaoT5](https://github.com/TaoT5)

---

<div align="center">

**Made with ❤️ by TravelAgent Team**

[⬆ 返回顶部](#travelagent-项目总览)

</div>

