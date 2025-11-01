package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;


import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    //具体业务名称，将前缀和业务名拼接之后当做Key
    private final String name;


    //这里不是@Autowired注入，采用的是构造器注入，在创建SimpleRedisLock时，将RedisTemplate作为参数传入
    private final StringRedisTemplate stringRedisTemplate;

    //构造函数
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    //锁的前缀
    private static final String KEY_PREFIX = "lock:";
    //锁的线程标识的前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";


    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();


        //获取锁
        Boolean isGetLock = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //因为Boolean是包装类，直接返回会有自动拆箱，可能会有安全风险
        //所以这里使用包装类的equals方法来判断
        return Boolean.TRUE.equals(isGetLock);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的线程标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //判断是否一致
        if (threadId.equals(id)) {
            //一致，释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

    }
}
