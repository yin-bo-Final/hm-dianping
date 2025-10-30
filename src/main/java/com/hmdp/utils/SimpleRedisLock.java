package com.hmdp.utils;

import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    //锁的前缀
    private static final String KEY_PREFIX = "lock:";

    //具体业务名称，将前缀和业务名拼接之后当做Key
    private String name;

    //这里不是@Autowired注入，采用的是构造器注入，在创建SimpleRedisLock时，将RedisTemplate作为参数传入
    private StringRedisTemplate stringRedisTemplate;

    //构造函数
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程表示
        long threadId = Thread.currentThread().getId();
        String threadIdStr = String.valueOf(threadId);

        //获取锁
        Boolean isGetLock = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadIdStr, timeoutSec, TimeUnit.SECONDS);
        //因为Boolean是包装类，直接返回会有自动拆箱，可能会有安全风险
        //所以这里使用包装类的equals方法来判断
        return Boolean.TRUE.equals(isGetLock);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
