# TravelAgent 部署指南

## 📖 目录

- [部署方式概览](#部署方式概览)
- [本地开发环境](#本地开发环境)
- [Docker 部署](#docker-部署)
- [生产环境部署](#生产环境部署)
- [云服务部署](#云服务部署)
- [监控与运维](#监控与运维)
- [故障排除](#故障排除)

---

## 部署方式概览

| 部署方式 | 适用场景 | 难度 | 成本 | 推荐指数 |
|---------|---------|------|------|---------|
| 本地开发 | 开发测试 | ⭐ | 免费 | ⭐⭐⭐⭐⭐ |
| Docker Compose | 小型团队 | ⭐⭐ | 低 | ⭐⭐⭐⭐ |
| Kubernetes | 生产环境 | ⭐⭐⭐⭐ | 中 | ⭐⭐⭐⭐⭐ |
| 云服务器 | 中型应用 | ⭐⭐⭐ | 中 | ⭐⭐⭐⭐ |
| Serverless | 低频使用 | ⭐⭐ | 低 | ⭐⭐⭐ |

---

## 本地开发环境

### 前置要求

```bash
# 检查 Java 版本
java -version  # 需要 21+

# 检查 Maven
mvn -version  # 需要 3.9+

# 检查 Node.js
node -v  # 需要 18+

# 检查 Docker (可选)
docker --version  # 需要 24+
```

### 快速启动

```bash
# 1. 克隆项目
git clone https://github.com/TaoT5/travel-agent.git
cd travel-agent

# 2. 配置环境变量
cp .env.travel-agent.example .env.travel-agent
# 编辑 .env.travel-agent，填写必要的 API Key

# 3. 编译项目
./mvnw clean install  # Linux/Mac
mvnw.cmd clean install  # Windows

# 4. 启动后端
./mvnw spring-boot:run -pl travel-agent-app

# 5. 启动前端 (新终端)
cd web
npm install
npm run dev

# 6. 访问应用
# 前端: http://localhost:3000
# 后端: http://localhost:8080
```

---

## Docker 部署

### 方式 1: Docker Compose (推荐)

#### 1. 启动 Milvus 向量数据库

```bash
# 启动 Milvus 及相关依赖 (etcd, MinIO)
docker-compose -f docker-compose.milvus.yml up -d

# 检查服务状态
docker-compose -f docker-compose.milvus.yml ps

# 查看日志
docker-compose -f docker-compose.milvus.yml logs -f
```

#### 2. 启动应用

```bash
# 构建并启动应用
docker-compose -f docker-compose.app.yml up -d --build

# 检查服务状态
docker-compose -f docker-compose.app.yml ps

# 查看日志
docker-compose -f docker-compose.app.yml logs -f travel-agent-app
docker-compose -f docker-compose.app.yml logs -f travel-agent-web
```

#### 3. 访问服务

```bash
# 前端
http://localhost:80

# 后端 API
http://localhost:8080

# API 文档
http://localhost:8080/swagger-ui.html
```

#### 4. 停止服务

```bash
# 停止所有服务
docker-compose -f docker-compose.milvus.yml down
docker-compose -f docker-compose.app.yml down

# 停止并删除数据卷 (⚠️ 会删除所有数据)
docker-compose -f docker-compose.milvus.yml down -v
docker-compose -f docker-compose.app.yml down -v
```

### 方式 2: 手动 Docker 构建

#### 1. 构建后端镜像

```bash
# 构建应用镜像
docker build -f Dockerfile.app -t travel-agent-app:latest .

# 构建 MCP 服务器镜像 (可选)
docker build -f Dockerfile.mcp -t travel-agent-mcp:latest .
```

#### 2. 运行容器

```bash
# 运行后端
docker run -d \
  --name travel-agent-app \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e MILVUS_HOST=milvus-standalone \
  -e MILVUS_PORT=19530 \
  --network travel-network \
  travel-agent-app:latest

# 运行前端
docker run -d \
  --name travel-agent-web \
  -p 80:80 \
  --network travel-network \
  travel-agent-web:latest
```

---

## 生产环境部署

### 1. 环境要求

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4 核 | 8 核 |
| 内存 | 8 GB | 16 GB |
| 磁盘 | 50 GB SSD | 100 GB SSD |
| 网络 | 100 Mbps | 1 Gbps |

### 2. 数据库配置

#### Milvus 生产配置

```yaml
# docker-compose.milvus.yml (生产环境)
version: '3.5'
services:
  etcd:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 4G
    volumes:
      - etcd-data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

  minio:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 4G
    volumes:
      - minio-data:/minio_data

  standalone:
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 8G
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - milvus-data:/var/lib/milvus

volumes:
  etcd-data:
  minio-data:
  milvus-data:
```

#### 应用配置

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/travel_agent
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7

# Milvus 配置
milvus:
  host: ${MILVUS_HOST}
  port: 19530
  collection-name: travel_knowledge_prod

# 日志配置
logging:
  level:
    root: WARN
    com.travalagent: INFO
  file:
    name: /var/log/travel-agent/app.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
```

### 3. 安全配置

#### 环境变量

```bash
# .env.production
# 数据库
DB_USERNAME=travel_agent_user
DB_PASSWORD=<STRONG_PASSWORD>

# OpenAI API
OPENAI_API_KEY=sk-...

# Milvus
MILVUS_HOST=milvus-standalone

# Session
SESSION_SECRET=<RANDOM_SECRET>

# JWT (如果使用)
JWT_SECRET=<RANDOM_SECRET>
JWT_EXPIRATION=86400000
```

#### Nginx 配置

```nginx
# /etc/nginx/sites-available/travel-agent
server {
    listen 80;
    server_name your-domain.com;
    
    # 强制 HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;
    
    # SSL 证书
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    
    # 前端
    location / {
        proxy_pass http://localhost:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # 后端 API
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # SSE 支持
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
    
    # 静态资源缓存
    location ~* \.(jpg|jpeg|png|gif|ico|css|js)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
    
    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
}
```

### 4. 部署脚本

```bash
#!/bin/bash
# deploy.sh

set -e  # 遇到错误立即退出

echo "🚀 开始部署 TravelAgent..."

# 1. 拉取最新代码
echo "📦 拉取代码..."
git pull origin main

# 2. 编译项目
echo "🔨 编译项目..."
./mvnw clean package -DskipTests

# 3. 构建 Docker 镜像
echo "🐳 构建镜像..."
docker-compose -f docker-compose.app.yml build

# 4. 停止旧服务
echo "⏹️  停止旧服务..."
docker-compose -f docker-compose.app.yml down

# 5. 启动新服务
echo "▶️  启动新服务..."
docker-compose -f docker-compose.app.yml up -d

# 6. 健康检查
echo "🏥 健康检查..."
sleep 10
if curl -f http://localhost:8080/actuator/health; then
    echo "✅ 部署成功！"
else
    echo "❌ 健康检查失败，回滚..."
    docker-compose -f docker-compose.app.yml down
    echo "🔄 回滚完成，请检查日志"
    exit 1
fi

echo "🎉 部署完成！"
```

---

## 云服务部署

### AWS 部署

#### 1. 使用 ECS (Elastic Container Service)

```yaml
# task-definition.json
{
  "family": "travel-agent",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "2048",
  "memory": "4096",
  "executionRoleArn": "arn:aws:iam::ACCOUNT:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "travel-agent-app",
      "image": "YOUR_ACCOUNT.dkr.ecr.REGION.amazonaws.com/travel-agent-app:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        }
      ],
      "secrets": [
        {
          "name": "OPENAI_API_KEY",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT:secret:travel-agent/keys:OPENAI_API_KEY::"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/travel-agent",
          "awslogs-region": "REGION",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### 2. 使用 RDS (PostgreSQL)

```bash
# 创建 RDS 实例
aws rds create-db-instance \
  --db-instance-identifier travel-agent-db \
  --db-instance-class db.t3.medium \
  --engine postgres \
  --master-username travel_agent \
  --master-user-password YOUR_PASSWORD \
  --allocated-storage 100 \
  --storage-type gp2 \
  --backup-retention-period 7
```

### 阿里云部署

#### 1. 使用 ACK (容器服务 Kubernetes)

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: travel-agent-app
  namespace: travel-agent
spec:
  replicas: 3
  selector:
    matchLabels:
      app: travel-agent-app
  template:
    metadata:
      labels:
        app: travel-agent-app
    spec:
      containers:
      - name: app
        image: registry.cn-hangzhou.aliyuncs.com/YOUR_NAMESPACE/travel-agent-app:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: travel-agent-app
  namespace: travel-agent
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 8080
  selector:
    app: travel-agent-app
```

---

## 监控与运维

### 1. Spring Boot Actuator

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

访问监控端点：
- 健康检查: http://localhost:8080/actuator/health
- 应用信息: http://localhost:8080/actuator/info
- 指标数据: http://localhost:8080/actuator/metrics
- Prometheus: http://localhost:8080/actuator/prometheus

### 2. Prometheus + Grafana

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'travel-agent'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

```yaml
# docker-compose.monitoring.yml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana
    depends_on:
      - prometheus

volumes:
  prometheus-data:
  grafana-data:
```

### 3. 日志管理

```bash
# 查看实时日志
docker-compose -f docker-compose.app.yml logs -f travel-agent-app

# 查看最近 100 行
docker-compose -f docker-compose.app.yml logs --tail=100 travel-agent-app

# 导出日志
docker-compose -f docker-compose.app.yml logs travel-agent-app > app.log
```

### 4. 备份策略

```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/backup/travel-agent"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "📦 开始备份..."

# 1. 备份数据库
docker-compose -f docker-compose.milvus.yml exec standalone \
  milvus-tools backup \
  --collection travel_knowledge \
  --output /backup/milvus_${TIMESTAMP}

# 2. 备份配置文件
tar -czf ${BACKUP_DIR}/config_${TIMESTAMP}.tar.gz \
  .env.travel-agent \
  docker-compose.*.yml \
  application-*.yml

# 3. 清理旧备份 (保留 30 天)
find ${BACKUP_DIR} -type f -mtime +30 -delete

echo "✅ 备份完成！"
```

---

## 故障排除

### 常见问题

#### Q1: 应用启动失败

```bash
# 1. 检查日志
docker-compose -f docker-compose.app.yml logs travel-agent-app

# 2. 检查环境变量
docker-compose -f docker-compose.app.yml exec travel-agent-app env

# 3. 检查数据库连接
docker-compose -f docker-compose.milvus.yml exec standalone \
  milvus-tools status

# 4. 检查端口占用
netstat -tuln | grep 8080
lsof -i :8080
```

#### Q2: 向量检索失败

```bash
# 1. 检查 Milvus 状态
curl http://localhost:9091/health

# 2. 检查集合是否存在
docker-compose -f docker-compose.milvus.yml exec standalone \
  milvus-tools list-collections

# 3. 重建集合
docker-compose -f docker-compose.milvus.yml exec standalone \
  milvus-tools drop-collection --name travel_knowledge
docker-compose -f docker-compose.milvus.yml exec standalone \
  milvus-tools create-collection --name travel_knowledge

# 4. 重新导入数据
./mvnw spring-boot:run -Dspring-boot.run.arguments="--reimport-data"
```

#### Q3: 内存不足

```bash
# 1. 查看内存使用
docker stats

# 2. 调整 Docker 内存限制
# docker-compose.app.yml
services:
  travel-agent-app:
    deploy:
      resources:
        limits:
          memory: 4G
        reservations:
          memory: 2G

# 3. 调整 JVM 参数
# Dockerfile.app
ENV JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
```

#### Q4: 性能问题

```bash
# 1. 启用性能分析
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-XX:+FlightRecorder"

# 2. 查看慢查询
curl http://localhost:8080/actuator/metrics/http.server.requests

# 3. 检查数据库性能
docker-compose -f docker-compose.milvus.yml exec standalone \
  milvus-tools query-performance

# 4. 添加缓存
# 参考 RAG 功能文档的性能优化部分
```

### 性能调优

#### JVM 参数

```bash
# 生产环境推荐配置
JAVA_OPTS="-Xms4g \
           -Xmx8g \
           -XX:+UseG1GC \
           -XX:MaxGCPauseMillis=200 \
           -XX:+HeapDumpOnOutOfMemoryError \
           -XX:HeapDumpPath=/var/log/travel-agent/heapdump.hprof \
           -Djava.security.egd=file:/dev/./urandom"
```

#### 数据库连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 30000
```

---

## 相关资源

- [README.md](../README.md) - 项目介绍
- [RAG 功能文档](rag-feature-guide.md) - RAG 详细说明
- [开发指南](development-guide.md) - 开发相关
- [Operations.md](operations.md) - 运维操作手册

---

**文档版本**: v1.0.0  
**最后更新**: 2026-04-11  
**维护者**: TravelAgent Team
