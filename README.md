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

## 开发过程中的设计取舍与问题复盘

这个项目的重点不是把中间件堆在一起，而是针对每个业务问题选择合适的一致性和可用性边界。下面记录几个最值得复盘的场景。

### 1. 缓存穿透、缓存击穿与缓存重建

商铺信息属于典型的“读多写少”数据，直接查询 MySQL 会让数据库承受大量重复读请求。因此项目把商铺详情放入 Redis，并在 `CacheClient` 中分别实现了三种查询策略。

#### 1.1 查询不存在的数据：缓存空值

当请求一个不存在的商铺 ID 时，如果只判断 Redis 未命中再查询数据库，恶意脚本可以用大量随机 ID 持续穿透缓存。

当前实现的流程是：

1. 查询 Redis。
2. Redis 没有该 key 时查询 MySQL。
3. MySQL 也没有数据时写入一个短 TTL 的空字符串。
4. 后续相同 ID 直接返回空结果，不再访问 MySQL。

缓存空值实现简单、没有误判，适合当前商铺数据规模。它的代价是会占用一部分 Redis 空间，因此空值必须设置较短的过期时间。

#### 1.2 热点 key 同时失效：互斥锁与逻辑过期

热点商铺缓存过期的瞬间，大量请求可能同时查询数据库并重建同一个 key，这就是缓存击穿。项目提供了两种策略：

| 策略 | 请求行为 | 一致性 | 可用性 | 适用场景 |
| --- | --- | --- | --- | --- |
| 互斥锁重建 | 一个线程查库并写缓存，其他线程等待重试 | 较高 | 重建期间会等待 | 不能返回旧数据的场景 |
| 逻辑过期 | 返回旧值，由抢到锁的线程异步重建 | 最终一致 | 高 | 商铺详情、活动描述等读多写少数据 |

逻辑过期并不是把 Redis key 删除，而是把过期时间写入 value。过期后仍然先返回旧数据，再由后台线程查询数据库并刷新缓存，从而避免请求洪峰直接打到 MySQL。

这里的取舍是：对本地生活展示类数据，短暂的旧数据通常比接口整体不可用更容易接受，因此优先保证可用性。

#### 1.3 缓存一致性边界

本地缓存并未引入广播清理机制，缓存更新依赖逻辑过期或物理 TTL。这样做避免了为每一次商铺更新增加 MQ 广播，但也意味着：

- 数据更新后，旧缓存可能在短时间内继续被读取。
- 集群节点之间不会同时刷新本地进程内数据（当前主要缓存位于 Redis）。
- 对需要强一致的写操作，仍然以 MySQL 为准，缓存只承担读优化职责。

### 2. 秒杀链路为什么从同步落库迁移到 Redis + Kafka

一个同步的秒杀请求通常包含“判断库存 → 扣减库存 → 创建订单”三步。如果每个请求都同步访问 MySQL，高并发下会出现连接池耗尽、行锁竞争和响应时间抖动。

项目的演进可以概括为：

1. **直接操作数据库**：实现最简单，但库存判断和扣减容易形成竞争，吞吐量受数据库锁限制。
2. **数据库条件更新**：使用 `stock > 0` 解决超卖，但每个请求仍然需要访问数据库；一人一单在多实例部署下也不能依赖 JVM 锁。
3. **Redis Lua 预扣 + Kafka 异步落库**：把高并发资格判断前移到 Redis，把订单持久化放到 Kafka 消费者中异步完成。

最终请求只在 Redis 中完成以下原子操作：

```text
读取 seckill:stock:{voucherId}
    ↓
校验库存 > 0
    ↓
SISMEMBER seckill:order:{voucherId} {userId}
    ↓
扣减库存、记录用户、记录 orderId 预扣关系
```

Lua 返回值约定如下：

- `0`：资格校验成功，允许投递 Kafka。
- `1`：库存不足。
- `2`：用户已经购买过该优惠券。

这样可以把“库存不超卖”和“一人一单”放进同一个 Redis 原子操作，不需要再用一个覆盖范围很大的分布式锁。

### 3. Kafka 订单消息如何保证尽量可靠

Redis 预扣和 Kafka 投递不是一个分布式事务，所以项目没有把它包装成“绝对一致”，而是设计了可观测、可重试和可补偿的边界。

#### 3.1 Producer 配置取舍

- `acks=all`：等待 ISR 中的副本确认后再认为消息写入成功。
- `enable.idempotence=true`：降低 Producer 网络重试产生重复批次的概率。
- `retries=10`：允许短暂网络抖动自动恢复。
- `max.block.ms` 和 `delivery.timeout.ms`：避免 Kafka 不可用时 HTTP 请求无限等待。
- 以 `voucherId` 作为 key：相同优惠券的消息进入同一个分区，保留分区内顺序。

Producer 会等待 Broker 的发送结果。明确失败时执行补偿 Lua；如果是超时或结果未知，则保留预扣关系并返回“待确认”的订单号，避免客户端和服务端同时误判后重复补偿。

#### 3.2 Consumer 为什么手动提交 offset

消费者处理顺序是：

1. 获取用户维度的 Redisson 锁，避免同一个用户的订单并发落库。
2. 调用独立事务 Bean，执行库存条件扣减和订单插入。
3. 事务成功提交后调用 `acknowledge()`。

如果数据库事务抛出异常，代码不会提交 offset，Kafka 会重新投递消息。事务 Bean 单独拆分，是为了确保调用经过 Spring 代理，`@Transactional` 真正生效，而不是在同一个类里通过 `this` 调用导致事务失效。

#### 3.3 重试、DLT 与幂等

主消费者使用固定间隔重试，连续失败后把消息发送到 `voucher-order-topic.DLT`。DLT 消费者不会盲目增加库存，而是先检查数据库：

- 订单已存在：说明数据库最终成功，直接确认 DLT 消息。
- 订单不存在：执行带 `orderId` 校验的补偿 Lua，删除预扣关系、移除用户购买标记并恢复库存。
- 查询或补偿失败：不确认 DLT offset，等待后续重试。

项目采用三层幂等保护：

1. Redis `SISMEMBER` 阻止正常入口的一人多单。
2. Consumer 查询 `(user_id, voucher_id)`，快速跳过重复消息。
3. MySQL 联合唯一索引作为最终约束，防止并发窗口下重复插入。

### 4. 为什么从 Redis Stream 迁移到 Kafka

Redis 仍然负责库存和资格校验，但订单消息通道迁移为 Kafka，主要考虑以下几点：

- Kafka 通过分区扩展消费并发，订单消费不再依赖单线程轮询。
- Consumer group、offset 和 DLT 让消息处理状态更容易观察和恢复。
- Kafka 更适合承载持续的订单事件流，Redis 可以专注于缓存和原子脚本。
- Spring Kafka 原生支持重试、手动确认和死信恢复流程。

迁移并没有删除旧 Redis Stream 中已经存在的数据，避免新旧版本切换时误删尚未落库的订单。完整的迁移步骤、配置和验收命令见 [`docs/redis-stream-to-kafka-migration.md`](docs/redis-stream-to-kafka-migration.md)。

### 5. AI 客服 Agent 的安全边界与执行流程

AI 客服不是一个可以随意调用数据库的聊天接口，而是一个受限的只读 Agent。一次消息请求的生命周期如下：

```text
校验输入长度和登录态
    ↓
用户级频率限制 + requestId 去重
    ↓
Redisson 获取会话锁
    ↓
加载 Redis 最近上下文，必要时从 MySQL 回填
    ↓
调用 OpenAI-compatible 模型
    ↓
模型选择只读工具 → 查询业务数据 → 将工具结果回传模型
    ↓
最多执行 3 轮工具调用，生成最终回答
    ↓
MySQL 持久化消息和工具审计，Redis 更新短上下文
```

#### 5.1 工具调用为什么比拼接数据库结果更可控

模型只看到工具描述和工具返回值，不能直接构造 SQL，也不能执行下单、退款、修改账户等写操作。工具层负责：

- 将自然语言条件转换为有限的查询条件。
- 对查询结果进行统一 JSON 化。
- 工具异常时返回明确的失败信息，避免模型编造查询结果。
- 记录工具名称和参数，方便审计和排查。

当前工具调用最多 3 轮，防止模型反复调用工具造成死循环或放大外部 API 成本。

#### 5.2 会话记忆与持久化

- Redis List 保存每个用户会话最近 16 条消息，并设置 30 天 TTL，降低每轮请求的上下文读取成本。
- MySQL 保存完整会话和消息历史，Redis 丢失后可以回填最近消息。
- `requestId` 用于处理网络重试，重复请求不会重复生成一条助手回复。
- 同一会话使用 Redisson 锁串行处理，避免两条消息同时读取到相同上下文。

#### 5.3 当前限流策略的边界

当前 AI 客服使用 Redis 自增计数器实现“每个用户每分钟最多 20 次”的固定窗口限流，适合控制模型调用成本。它不是严格的滑动窗口：窗口边界处可能出现短时间突发请求。

如果未来需要更严格的风控，可以把它升级为 Redis ZSet 滑动窗口或令牌桶；但对于当前客服成本控制，固定窗口的实现复杂度和效果更匹配。

### 6. Redis 数据结构在业务中的具体应用

项目根据访问模式选择 Redis 数据结构，而不是所有数据都使用 String：

| 数据结构 | 业务场景 | 关键操作 |
| --- | --- | --- |
| String | 商铺缓存、秒杀库存、验证码、分布式 ID 计数 | `GET`、`SET`、`INCRBY` |
| Hash | 登录用户 Token、秒杀预扣订单关系 | `HSET`、`HGET`、`HDEL` |
| Set | 一人一单、关注集合、共同关注 | `SADD`、`SISMEMBER`、`SINTER` |
| ZSet | 博客点赞用户、点赞排行、关注 Feed 时间排序 | `ZADD`、`ZRANGE`、按 score 查询 |
| GEO | 按距离查询附近商铺 | `GEOADD`、半径查询 |
| Bitmap | 用户按月签到和连续签到统计 | `SETBIT`、`BITFIELD` |
| List | AI 会话短期上下文 | `RPUSH`、`LTRIM`、`LRANGE` |

例如，附近商户使用商铺类型作为 GEO key，查询时让 Redis 完成距离排序；共同关注使用两个 Set 的交集；签到使用一个 bit 表示当月一天，避免为每个签到日保存完整行记录。

### 7. 分布式锁和分布式 ID 的实现思路

项目保留了轻量级 `SimpleRedisLock`，并在需要可重入、续期和集群语义的场景使用 Redisson：

- 加锁使用 `SETNX + TTL`，避免锁永久占用。
- value 包含实例和线程标识，释放锁通过 Lua 比对标识后删除，防止误删其他线程的锁。
- 订单持久化、AI 会话和缓存重建分别使用不同的锁粒度，避免一把大锁把无关请求串行化。

订单 ID 使用 Redis 自增序列和时间戳组合生成：高位保存相对时间，低位保存当天序列号，既能保持趋势递增，也避免依赖数据库自增主键在高并发下的瓶颈。

## 关键接口与数据流

| 模块 | 入口示例 | 核心数据流 |
| --- | --- | --- |
| 商铺详情 | `GET /shop/{id}` | Redis 缓存 → 逻辑过期/互斥重建 → MySQL |
| 附近商铺 | `GET /shop/of/type` | Redis GEO → 距离排序 → 商铺详情批量查询 |
| 秒杀下单 | `POST /voucher-order/seckill/{id}` | Redis Lua → Kafka → MySQL 事务 → offset |
| AI 会话 | `POST /ai/customer-service/conversations/{id}/messages` | Redis 上下文 → 工具调用 → MySQL 审计 |
| 博客点赞 | `PUT /blog/like/{id}` | Redis ZSet 记录用户 → MySQL 更新计数 |
| 用户签到 | `POST /user/sign` | Redis Bitmap 写入当月 bit → `BITFIELD` 统计 |

## 测试关注点

除了普通的 Controller 和 Service 测试，项目专门覆盖了容易出错的边界：

- 秒杀 Lua 的库存不足、重复下单和补偿幂等。
- Kafka Producer 发送成功、发送失败和发送结果未知。
- Consumer 只有事务成功后才确认 offset。
- 消费异常进入 DLT 后，数据库已存在和不存在两种分支。
- MySQL 唯一索引与重复 Kafka 消息共同作用时只生成一笔订单。
- AI 工具搜索条件转换、输入长度、请求去重、会话锁和限流。
- 逻辑过期缓存返回旧值并异步重建，互斥锁只允许一个线程查库。

建议先运行不依赖外部服务的测试，再启动 MySQL、Redis 和 Kafka 做完整验收：

```bash
mvn -Dtest=VoucherOrderServiceImplTest,VoucherOrderServiceKafkaTest,VoucherOrderPersistenceServiceTest test
mvn test
```

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
