# TravelAgent 开发指南

> 本文档默认站在 **macOS Terminal** 的视角编写，假设你使用 `zsh` 或 `bash`，并统一使用 `./mvnw`、`python3` 和 `docker compose`。

## 目录

- 环境搭建
- macOS 本地约定
- 快速开始
- local-demo 与质量回放
- 项目结构
- 开发流程
- 测试指南
- 调试与排错
- 贡献建议

---

## 环境搭建

### 前置要求

| 工具 | 版本 | 用途 |
| --- | --- | --- |
| Java | 21+ | 后端运行与构建 |
| Node.js | 18+ | 前端开发 |
| npm | 10+ | 前端依赖管理 |
| Python | 3.11+ | 离线脚本与评测回放 |
| Docker Desktop | 24+ | Milvus 与容器化联调 |
| Git | 2.40+ | 版本控制 |

### macOS 本地约定

推荐使用 Homebrew 安装本地工具：

```bash
brew install openjdk@21 node python
```

安装后先确认版本：

```bash
java -version
node -v
npm -v
python3 --version
docker compose version
```

如果当前 shell 里的 `java` 不是 21，先设置：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

如果你使用 `zsh`，可以写入 `~/.zshrc`：

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/Sinlair/TravelAgent.git
cd TravelAgent
```

### 2. 准备环境变量

```bash
cp .env.travel-agent.example .env.travel-agent
```

至少确认下面这些配置：

- `SPRING_AI_OPENAI_API_KEY`
- `SPRING_AI_OPENAI_BASE_URL`
- `SPRING_AI_OPENAI_CHAT_MODEL`
- `TRAVEL_AGENT_TOOL_PROVIDER`
- `TRAVEL_AGENT_AMAP_API_KEY`
- `VITE_AMAP_WEB_KEY`
- `VITE_AMAP_SECURITY_JS_CODE`

### 3. 安装依赖

```bash
./mvnw -B -DskipTests package
cd web
npm ci
cd ..
```

### 4. 启动后端

```bash
./mvnw -f travel-agent-app/pom.xml spring-boot:run
```

### 5. 启动前端

```bash
cd web
npm run dev
```

默认地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`

### 6. 停止服务

在运行中的 Terminal 标签页里使用 `Control-C`。

---

## local-demo 与质量回放

### local-demo

如果你不想依赖 OpenAI、MCP 或 Milvus，本地优先使用 `local-demo`：

```bash
./mvnw -pl travel-agent-app -am spring-boot:run -Dspring-boot.run.profiles=local-demo
```

这个 profile 适合：

- 本地 smoke 验证
- UI 联调
- 质量场景回放
- CI 中的稳定评测路径

### 质量回放

在 `local-demo` 启动后运行：

```bash
python3 scripts/run_quality_scenarios.py --base-url http://localhost:8080
```

输出目录：

- `data/exports/quality-reports/quality-scenarios.latest.json`
- `data/exports/quality-reports/quality-scenarios.latest.md`

当场景结果偏离 fixture 中定义的预期结果或预期 agent 路由时，脚本会返回非零退出码。

### 反馈分析

```bash
python3 scripts/analyze_feedback_loop.py
```

输出目录：

- `data/exports/`

---

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

主要模块：

- `travel-agent-app`
  REST API、SSE、健康检查、会话编排入口
- `travel-agent-domain`
  领域模型、实体、值对象、仓储与网关契约
- `travel-agent-infrastructure`
  检索、Agent、持久化、校验、修复、增强
- `travel-agent-amap`
  高德 HTTP 适配层
- `travel-agent-amap-mcp-server`
  面向高德工具的独立 MCP Server
- `travel-agent-types`
  共享响应包装、异常与类型定义
- `web`
  Vue 3 前端工作台
- `scripts`
  评测、知识处理、反馈分析脚本

---

## 开发流程

### 常见循环

```bash
git checkout -b feature/your-change
./mvnw -B test
cd web && npm run test && npm run build
```

完成后：

```bash
git add .
git commit -m "feat: your change"
git push origin feature/your-change
```

### 修改后端功能时

- 先看 `travel-agent-domain` 的契约和模型是否需要调整
- 再看 `travel-agent-app` 的编排和 DTO 是否受影响
- 最后在 `travel-agent-infrastructure` 中实现具体逻辑

### 修改前端功能时

- 优先复用现有的共享结果模型和状态处理
- 保持 `chat / itinerary / map / timeline / feedback` 面板状态一致
- 改动接口字段时同步更新 `web/src/types/api.ts`

---

## 测试指南

### 后端

```bash
./mvnw -B test
```

### 仅打包后端

```bash
./mvnw -pl travel-agent-app -am -DskipTests package
```

### 前端

```bash
cd web
npm run test
npm run build
```

### 手工 smoke

```bash
java -jar travel-agent-app/target/travel-agent-app.jar --server.port=18080
curl http://localhost:18080/actuator/health
```

---

## 调试与排错

`./mvnw` 可以编译，但 `java` 仍然指向旧版本：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

没有 `python`：

```bash
python3 --version
```

在 macOS 上统一使用 `python3`，不要依赖 `python` 别名。

Docker 相关命令不可用：

- 确认 Docker Desktop 已经启动
- 在 macOS 上使用 `docker compose`，不要继续沿用旧的 `docker-compose`

端口冲突时：

- 后端改 `--server.port`
- 前端改 `vite --port <port>` 或 `npm run dev -- --port <port>`

---

## 贡献建议

- 文档和命令默认保持 macOS 口径
- 如果必须补充其他平台说明，单独标注，不要混入主路径
- 改动接口字段时同步检查：
  `README`、`docs/operations.md`、`docs/release-checklist.md`、`web/src/types/api.ts`
