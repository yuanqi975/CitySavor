<div align="center">
  <h1>HM DianPing Plus · 本地生活服务平台</h1>
  <p>
    <img src="https://img.shields.io/badge/Java-8-orange" alt="Java 8">
    <img src="https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen" alt="Spring Boot">
    <img src="https://img.shields.io/badge/MySQL-5.7%2B-blue" alt="MySQL">
    <img src="https://img.shields.io/badge/Redis-6%2B-red" alt="Redis">
    <img src="https://img.shields.io/badge/Kafka-3.8.1-black" alt="Kafka">
    <img src="https://img.shields.io/badge/MyBatis--Plus-3.4.3-blueviolet" alt="MyBatis-Plus">
  </p>
</div>

<br/>

**HM DianPing Plus** 是一个面向本地生活场景的后端项目，提供用户、商铺、优惠券、秒杀、探店内容、关注互动和 AI 客服等能力。

这个仓库不仅实现业务功能，也记录了我围绕高并发、缓存、消息可靠性和 AI Agent 落地所做的架构改造。项目的重点是：把“能完成一次请求”进一步设计成“在并发、失败和重复投递下仍然可验证、可补偿”。

## 项目亮点

### 1. Redis Lua + Kafka 的高并发秒杀链路

秒杀请求不会直接把库存判断和订单写入都压到 MySQL，而是拆成两个阶段：

```text
HTTP 秒杀请求
    │
    ▼
Redis Lua：原子校验库存 + 一人一单 + 预扣库存 + 生成订单关系
    │
    ├─ Kafka 投递成功 → 消费者事务落库 → 手动提交 offset
    │
    └─ 明确投递失败 → Lua 补偿预扣库存和用户下单标记
```

实现要点：

- 用 Redis Lua 将“库存判断、一人一单、预扣库存”放在同一个原子操作中，避免超卖和并发窗口。
- 以 `voucherId` 作为 Kafka key，让同一优惠券的订单保持分区内有序。
- Producer 使用 `acks=all`、幂等生产和有限重试；Consumer 关闭自动提交，只有 MySQL 事务成功后才确认 offset。
- 消费失败经过重试后进入 DLT；DLT 消费者会先核对数据库，再按订单号幂等执行 Redis 补偿。
- MySQL 使用 `(user_id, voucher_id)` 联合唯一索引作为最终数据约束，抵御重复消息和并发竞争。

详细迁移说明见 [`docs/redis-stream-to-kafka-migration.md`](docs/redis-stream-to-kafka-migration.md)。

### 2. 从“缓存能用”到缓存风险治理

`CacheClient` 针对不同读场景提供了三种策略：

- **缓存穿透**：数据库查不到的数据写入短 TTL 空值，避免恶意 ID 持续打到数据库。
- **缓存击穿**：支持互斥锁重建缓存，以及逻辑过期后返回旧值、异步重建的方案。
- **分布式互斥**：使用 Redis 原子写入和 Redisson 锁控制重建、客服会话等并发场景。

这里的取舍是明确的：对商铺详情、活动信息等读多写少的数据，优先保证可用性，并通过过期策略实现最终一致。

### 3. 可控、可审计的 AI 客服 Agent

项目接入 OpenAI Chat Completions 兼容协议，但没有让模型直接操作业务数据或执行写操作。Agent 只能通过受限的只读工具获取事实：

- 商铺搜索
- 商铺详情
- 商铺优惠券
- 商铺评论
- 当前用户订单

同时提供：

- Redis 保存最近 16 条会话消息，MySQL 持久化完整会话和工具调用审计记录。
- `requestId` 去重，避免前端重试导致重复消息。
- Redisson 会话锁，防止同一会话并发生成多条回答。
- Redis 用户级限流（每分钟最多 20 次）和单条消息长度校验。
- AI 默认关闭；未配置模型时接口仍可启动，但不会发起模型请求。

客服接口和配置说明见 [`docs/ai-customer-service.md`](docs/ai-customer-service.md)。

### 4. 完整的本地生活业务闭环

- 手机验证码登录、Token 刷新、签到统计
- 商铺分类、详情、名称搜索、附近商户查询
- 优惠券发布、查询和秒杀下单
- 探店笔记发布、点赞、热门排行、关注 Feed
- 商铺评论和分页查询
- 共同关注、文件上传等基础能力

## 技术栈

| 层次 | 技术 | 用途 |
| --- | --- | --- |
| Web | Spring Boot 2.3.12、Spring MVC | REST API、统一异常处理、登录拦截 |
| 持久化 | MySQL、MyBatis-Plus | 业务数据、订单最终落库、唯一约束 |
| 缓存与并发 | Redis、Lettuce、Redisson、Lua | 缓存、分布式锁、秒杀原子校验、补偿 |
| 消息 | Apache Kafka、Spring Kafka | 秒杀订单异步化、重试、DLT |
| 工具 | Hutool、Lombok | JSON、字符串、ID 和通用开发工具 |
| AI | OpenAI-compatible API、Function/Tool Calling | 受控的只读客服 Agent |
| 测试 | JUnit、Spring Boot Test、JMeter | 单元测试、集成测试和压测验证 |

## 目录结构

```text
src/main/java/com/hmdp
├── controller/       REST 接口
├── service/          业务服务与事务边界
├── mq/               Kafka 生产者、消费者和 DLT 处理
├── ai/               AI 客服客户端、记忆和工具定义
├── utils/            Redis 缓存、分布式锁、ID 生成等基础设施
├── entity/mapper/    数据模型与 MyBatis-Plus 映射
└── config/           Web、Redis、Kafka 和全局异常配置
src/main/resources
├── db/               初始化表结构、迁移和示例数据
├── mapper/           MyBatis XML
├── seckill.lua       秒杀预扣与一人一单脚本
└── seckill-compensate.lua
```

## 本地运行

### 环境要求

- JDK 8
- Maven 3.6+
- MySQL 5.7+
- Redis（默认 `127.0.0.1:6380`）
- Docker Desktop（用于启动 Kafka）

### 1. 初始化 MySQL

创建 `hmdp` 数据库后执行：

```bash
mysql -uroot -p hmdp < src/main/resources/db/hmdp.sql
```

如果要启用 AI 客服，再执行：

```bash
mysql -uroot -p hmdp < src/main/resources/db/ai_customer_service.sql
```

项目默认数据库连接位于 `src/main/resources/application.yaml`，请按本机环境修改账号、密码和端口。

### 2. 启动 Kafka

```bash
docker compose -f docker-compose.kafka.yml up -d
docker compose -f docker-compose.kafka.yml ps
```

如需更换 Kafka 地址，可通过环境变量覆盖：

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:9092"
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

后端默认监听 `http://localhost:8081`。

### 4. 配置 AI 客服（可选）

AI 默认关闭。使用 OpenAI-compatible 服务时设置：

```powershell
$env:AI_ENABLED="true"
$env:AI_BASE_URL="https://your-provider.example/v1"
$env:AI_API_KEY="your-secret-key"
$env:AI_MODEL="your-model-name"
$env:AI_TIMEOUT_MS="30000"
```

后端主要接口（如果通过前端 Nginx 代理，通常会在前面加上 `/api`）：

```text
POST /ai/customer-service/conversations
GET  /ai/customer-service/conversations/{id}/messages
POST /ai/customer-service/conversations/{id}/messages
```

## 测试与验证

运行全部测试：

```bash
mvn test
```

编译检查：

```bash
mvn -DskipTests compile
```

项目测试覆盖秒杀 Lua、Kafka Producer/Consumer、DLT、事务落库、订单持久化、AI 工具查询、缓存接口和业务校验等关键路径；也可以使用 JMeter 对秒杀接口进行并发验证。

## 设计边界

Redis 预扣和 Kafka 投递属于两个独立系统，当前实现通过“投递失败补偿 + Consumer 重试/DLT + 数据库对账约束”保证业务可恢复，但并不把二者描述成一个跨系统分布式事务。

如果用于生产环境，建议进一步补充预扣记录、后台对账任务、告警和 Outbox/事务消息等机制，以覆盖进程在 Lua 成功后、Kafka 调用前突然退出等极端窗口。

## 致谢

项目业务场景参考了经典本地生活服务项目，并在学习和重构过程中加入了 Kafka 订单链路、AI 客服 Agent、补偿机制和测试验证等改造。
