-- Kafka 投递最终失败或订单进入死信队列时，撤销 Redis 预扣。
-- SISMEMBER + SREM + INCRBY 在一个 Lua 脚本中完成，重复执行不会重复增加库存。
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local reservationKey = 'seckill:reservation:' .. voucherId

if redis.call('hget', reservationKey, userId) ~= orderId then
    return 0
end

if redis.call('sismember', orderKey, userId) == 0 then
    return 0
end

redis.call('hdel', reservationKey, userId)
redis.call('srem', orderKey, userId)
redis.call('incrby', stockKey, 1)
return 1
