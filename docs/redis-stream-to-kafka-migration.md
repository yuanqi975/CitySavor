# 秒杀订单从 Redis Stream 迁移到 Kafka

## 1. 迁移结果

本次迁移只替换秒杀订单的异步消息通道：

```text
HTTP 秒杀请求
  -> Redis Lua 校验资格、预扣库存、记录用户
  -> Kafka voucher-order-topic
  -> voucher-order-group 消费
  -> 独立事务服务扣减 MySQL 库存并创建订单
  -> 事务提交后手动提交 Kafka offset
```

Redis 的缓存、Lua、ID 生成器以及 Redisson 分布式锁仍然保留。原来的
`stream.orders`、`XREADGROUP`、`XACK`、pending-list 和单线程轮询器已经从代码中移除。

## 2. 文件变更

| 文件 | 作用 |
| --- | --- |
| `pom.xml` | 引入 `spring-kafka`，由 Spring Boot 2.3.12 管理兼容版本 |
| `docker-compose.kafka.yml` | 以 KRaft 模式启动单节点 Kafka |
| `application.yaml` | 配置生产者、消费者、手动确认、序列化和 Topic 名称 |
| `seckill.lua` | 做 Redis 资格校验、预扣库存，并记录用户与订单 ID 的预扣关系 |
| `seckill-compensate.lua` | Kafka 失败或 DLT 最终失败时幂等恢复 Redis |
| `KafkaConfig.java` | 创建主 Topic/DLT，配置有限重试和同步投递 DLT |
| `KafkaTopicProperties.java` | 集中绑定 Topic 和发送超时配置 |
| `VoucherOrderProducer.java` | 以 `voucherId` 为 key 发送订单并等待 Broker 确认 |
| `VoucherOrderConsumer.java` | 消费主 Topic，事务成功后手动确认 offset |
| `VoucherOrderDltConsumer.java` | 处理最终失败订单并补偿 Redis |
| `VoucherOrderPersistenceService.java` | 独立的公共事务 Bean，完成 MySQL 扣库存和订单插入 |
| `VoucherOrderReservationService.java` | 封装 Redis 补偿脚本 |
| `VoucherOrderServiceImpl.java` | 执行 Lua 后生产 Kafka 消息，发送失败时补偿 |
| `hmdp.sql` | 新建数据库时直接包含联合唯一索引 |
| `kafka_voucher_order_migration.sql` | 已存在数据库的一次性索引迁移脚本 |

## 3. 本地启动 Kafka

不需要在 Windows 原生安装 Kafka，但必须先启动 Docker Desktop。然后在项目根目录运行：

```powershell
docker compose -f docker-compose.kafka.yml up -d
docker compose -f docker-compose.kafka.yml ps
docker compose -f docker-compose.kafka.yml logs -f kafka
```

Compose 使用 `apache/kafka:3.8.1`，对宿主机暴露 `127.0.0.1:9092`。Kafka
关闭了自动创建 Topic；应用启动时，Spring `KafkaAdmin` 会根据两个 `NewTopic` Bean 创建：

```text
voucher-order-topic      3 partitions, replication-factor 1
voucher-order-topic.DLT  3 partitions, replication-factor 1
```

也可以用 CLI 检查：

```powershell
docker exec hmdp-kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 --list

docker exec hmdp-kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 `
  --describe --topic voucher-order-topic
```

停止但保留消息数据：

```powershell
docker compose -f docker-compose.kafka.yml stop
```

删除容器但保留命名卷：

```powershell
docker compose -f docker-compose.kafka.yml down
```

不要执行 `down -v`，除非明确需要同时删除本地 Kafka 消息数据。

## 4. 数据库迁移

### 4.1 新数据库

重新导入 `src/main/resources/db/hmdp.sql` 即可，`tb_voucher_order` 已包含：

```sql
UNIQUE INDEX uk_user_voucher(user_id, voucher_id)
```

### 4.2 已存在的数据库

先执行重复数据检查：

```sql
SELECT user_id, voucher_id, COUNT(*) AS duplicate_count
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;
```

结果为空后，再执行：

```sql
ALTER TABLE tb_voucher_order
    ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
```

项目已提供 `src/main/resources/db/kafka_voucher_order_migration.sql`。这个脚本只能执行一次；
索引已存在时再次执行会报重复索引名。

联合唯一索引是消费端幂等的最终防线。Java 中的存在性查询用于快速跳过重复消息，数据库约束用于封堵并发竞争窗口。

## 5. Kafka 配置讲解

### 5.1 生产者

关键配置如下：

```yaml
acks: all
retries: 10
properties:
  enable.idempotence: true
  max.in.flight.requests.per.connection: 5
  max.block.ms: 5000
  request.timeout.ms: 5000
  delivery.timeout.ms: 10000
```

- `acks=all`：等待当前 ISR 中所有副本确认。单节点开发环境实际只有一个副本。
- `enable.idempotence=true`：同一个生产者会话内发生网络重试时，Broker 去除重复批次。
- `retries=10`：允许客户端自动重试；总时间仍受 `delivery.timeout.ms` 限制。
- `max.in.flight...=5`：满足幂等生产者要求，并避免重试导致顺序错乱。
- `max.block.ms`：Broker 不可达、取不到元数据时，限制接口阻塞时间。

`VoucherOrderProducer.send()` 使用：

```java
kafkaTemplate.send(topic, String.valueOf(order.getVoucherId()), order)
        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
```

同步等待 `Future` 是为了让 HTTP 层知道消息是否拿到了 Broker 确认。key 使用
`voucherId`，因此同一张优惠券的消息进入同一分区并保持分区内顺序。

### 5.2 消费者

```yaml
enable-auto-commit: false
listener:
  ack-mode: manual_immediate
  concurrency: 3
```

消费者组为 `voucher-order-group`，并发度与三个分区一致。监听器的执行顺序是：

1. 获取 Redisson 用户订单锁。
2. 调用独立事务 Bean `VoucherOrderPersistenceService.createOrder()`。
3. 条件更新 MySQL：`stock = stock - 1 WHERE voucher_id = ? AND stock > 0`。
4. 插入 `tb_voucher_order`。
5. 事务代理提交事务并返回。
6. 调用 `acknowledgment.acknowledge()` 提交 offset。

数据库方法抛出异常时，第 6 步不会执行，异常继续交给 Kafka 容器处理。因此消息语义是“至少一次”，而不是“最多一次”。

事务逻辑被放到独立 Spring Bean 中，是为了确保调用经过事务代理。若把 `@Transactional`
方法放在 `VoucherOrderServiceImpl` 内再用 `this` 调用，Spring AOP 无法开启事务。

## 6. 重试、DLT 和 offset

主消费者使用：

```java
new FixedBackOff(1000L, 3L)
```

含义是首次消费失败后最多再重试三次，每次间隔一秒。仍失败则把消息发送到
`voucher-order-topic.DLT`。

项目基于 Spring Kafka 2.5.14。该版本默认的 `DeadLetterPublishingRecoverer` 对 DLT
发送是异步的，因此本项目覆盖了它的 `publish()`，同步等待 DLT Broker 确认。只有 DLT
确认成功，错误处理器才允许提交原 Topic 的 offset，避免“原消息已跳过、DLT 又没写成功”。

DLT 消费者先查询 MySQL：

- 订单已存在：说明数据库最终已经提交，不补偿 Redis，只确认 DLT offset。
- 订单不存在：执行 Redis 补偿脚本，成功后确认 DLT offset。
- 查询数据库或补偿 Redis 失败：不确认，DLT 每五秒持续重试，不再生成 `.DLT.DLT`。

## 7. Redis 补偿为何幂等

补偿 Lua 的核心逻辑是：

```lua
if redis.call('hget', reservationKey, userId) ~= orderId then
    return 0
end
if redis.call('sismember', orderKey, userId) == 0 then
    return 0
end
redis.call('hdel', reservationKey, userId)
redis.call('srem', orderKey, userId)
redis.call('incrby', stockKey, 1)
```

补偿首先要求 `seckill:reservation:{voucherId}` 中记录的订单 ID 与当前消息完全相同，防止旧 DLT
消息撤销用户后来发起的新预扣。检查标记、删除标记和增加库存都在 Redis 单个 Lua 脚本中原子执行。第一次补偿返回 1；
后续重复补偿发现用户标记已不存在，直接返回 0，不会重复增加库存。

补偿发生在两个位置：

- Redis 预扣成功，但 Kafka 在创建发送任务前明确失败。
- 主消费者经过有限重试后进入 DLT，且数据库确认不存在该订单。

## 8. 幂等边界

这套实现包含三层防线：

1. Redis `SISMEMBER` 阻止正常入口的一人多单。
2. 消费事务开始时查询 `(user_id, voucher_id)`，重复 Kafka 消息直接视为成功。
3. MySQL 联合唯一索引处理并发竞争，是最终一致性约束。

Kafka 生产者幂等只解决生产者重试造成的重复批次，不能代替业务唯一索引。Kafka 的
“至少一次”意味着数据库必须能够安全处理同一事件多次。

## 9. 启动顺序

1. 启动 MySQL，并确认 `hmdp` 库可访问。
2. 启动 Redis，当前默认地址是 `127.0.0.1:6380`。
3. 启动 Docker Desktop 和 Kafka Compose。
4. 对既有数据库执行唯一索引迁移。
5. 启动 Spring Boot 应用。

Kafka 地址可以覆盖：

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:9092"
mvn spring-boot:run
```

## 10. 验收测试

### 10.1 自动测试

只运行本次迁移的测试：

```powershell
mvn '-Dtest=VoucherOrderServiceImplTest,VoucherOrderServiceKafkaTest,VoucherOrderPersistenceServiceTest,VoucherOrderProducerTest,VoucherOrderConsumerTest,VoucherOrderDltConsumerTest' test
```

验证内容包括：

- 秒杀 Lua 不再包含 `XADD` 或 `stream.orders`。
- 补偿 Lua 具有 `SISMEMBER` 幂等保护。
- 生产者使用 `voucherId` 作为 key。
- 消费事务成功后才确认 offset。
- 消费异常时不确认 offset。
- Kafka 明确发送失败时调用 Redis 补偿；投递结果不明时保留预扣等待对账。
- DLT 只补偿数据库中不存在的订单。

编译检查：

```powershell
mvn -DskipTests compile
```

### 10.2 正常秒杀

调用原接口 `POST /voucher-order/seckill/{voucherId}` 后检查：

```sql
SELECT * FROM tb_voucher_order ORDER BY create_time DESC;
SELECT voucher_id, stock FROM tb_seckill_voucher WHERE voucher_id = ?;
```

检查消费者组：

```powershell
docker exec hmdp-kafka /opt/kafka/bin/kafka-consumer-groups.sh `
  --bootstrap-server localhost:9092 `
  --describe --group voucher-order-group
```

### 10.3 重复消息

向主 Topic 重复发送完全相同的订单 JSON，最终应满足：

```sql
SELECT user_id, voucher_id, COUNT(*)
FROM tb_voucher_order
GROUP BY user_id, voucher_id
HAVING COUNT(*) > 1;
```

查询结果为空，MySQL 库存只扣一次。

### 10.4 消费失败与 DLT

临时让 `createOrder()` 抛出异常，观察三次重试后消息进入 DLT：

```powershell
docker exec hmdp-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic voucher-order-topic.DLT --from-beginning
```

数据库没有订单时，Redis 库存应恢复、用户下单标记应移除。

### 10.5 Kafka 不可用

```powershell
docker compose -f docker-compose.kafka.yml stop kafka
```

发起秒杀后，若发送任务在交给 Kafka 客户端前明确失败，接口返回“订单消息发送失败”并补偿。
若 `Future` 超时或返回执行异常，消息可能已经被 Broker 接收，接口会返回“订单投递状态待确认”和订单号，
并保留 Redis 预扣等待 Kafka 恢复消费或人工对账，避免误补偿导致超卖。随后恢复：

```powershell
docker compose -f docker-compose.kafka.yml start kafka
```

## 11. 需要明确的可靠性限制

Redis Lua 和 Kafka 发送属于两个不同系统，无法组成一个原子事务。本次实现严格按约定采用
“发送异常后补偿”，可以覆盖 Kafka 明确不可用等常见失败，但仍存在两个极端窗口：

- 进程在 Lua 成功后、调用 Kafka 前直接崩溃，来不及补偿。
- Broker 实际写入成功，但确认响应丢失导致客户端超时；此时实现会保留预扣并返回订单号供对账。

如果要升级为生产级资金/库存链路，应增加可恢复的预扣记录和后台对账任务，或者引入可靠
Outbox/事务消息设计。当前实现适合作为本项目的 Kafka 至少一次 + 消费幂等迁移方案，但不应被描述为 Redis 与 Kafka 之间的严格分布式事务。

## 12. 旧 Stream 数据处理

代码不会自动删除 Redis 中已有的 `stream.orders`。迁移上线前应先让旧消费者清空积压，停止
旧版本应用，再切换到 Kafka。确认旧 Stream 不再需要后才手工删除；不要在新旧版本同时运行时删除，否则可能丢失尚未落库的旧订单。
