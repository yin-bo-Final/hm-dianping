-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]


-- 优惠券key
local stockKey = 'seckill:stock:' .. voucherId

-- 订单key
local orderKey = 'seckill:order:' .. voucherId


-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
-- 判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
-- 扣减库存
redis.call('incrby', stockKey, -1)
-- 将userId存入当前优惠券的set集合
redis.call('sadd', orderKey, userId)
-- 发送消息到stream队列当中，XADD stream.orders * k1 v1 k2 v2....
-- 在VoucherOrder中订单的id就叫id  所以这里将订单ID命名为id
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0