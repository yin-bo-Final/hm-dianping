local key = KEYS[1]; --锁的标示
local threadId = ARGV[1]; --线程的唯一标示
local releaseTime = ARGV[2]; --锁的自动释放时间

--如果锁不存在
if (redis.call('exists',key) == 0)then
    --获取锁并且添加线程标示，state设为1
    redis.call('hset',key,threadId,'1'); --第三个参数为锁的计数器
    --设置锁的有效期
    redis.call('expire',key,releaseTime);
    return 1; -- 返回结果
end;

--锁已经存在，判断线程标识是否为自己的
if (redis.call('hexists',key,threadId) == 1)then
    --是自己的，锁计数器+1，使用hincrby  使自增长的值为1
    redis.call('hincrby',key,threadId,1);
    --设置锁的有效期
    redis.call('expire',key,releaseTime);
    return 1; -- 返回结果
end;
return 0;--代码走到这里，说明锁存在而且不在同一线程，获取锁失败了

