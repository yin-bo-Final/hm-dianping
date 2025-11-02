local key = KEYS[1];--其中KEYS[1]为锁的key
local threadId = ARGV[1];--ARGV[1]为当前线程标示
local releaseTime = ARGV[2];--ARGV[2]为锁的自动施放时间

-- 如果锁不是自己的 也就是说threadId不存在，锁过期或者被人释放了

--只有加锁的线程，才会在 Redis 的 Hash 中 写入自己的 threadId
-- 没写就说明不是自己的锁
if (redis.call('HEXISTS',key,threadId) == 0)then
    return nil; --直接返回nil  使脚本结束
end

--如果锁是自己的 锁计数器-1 还是使用hincrby,不过自增长的值为-1
local count= redis.call('HINCRBY',key,threadId,-1);

--判断重入数次为多少
if(count > 0)then
    --大于0 说明锁还没有释放，重置有效期
    redis.call('expire',key,releaseTime)
end

    --否则直接释放锁
    redis.call('del',key)
    return nil


