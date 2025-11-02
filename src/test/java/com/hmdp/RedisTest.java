package com.hmdp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


@SpringBootTest
public class RedisTest {
    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    void setUp() {
        lock = redissonClient.getLock("lock");
    }

    @Test
    void method1() throws InterruptedException {
        boolean success = lock.tryLock(1L, TimeUnit.SECONDS);
        if (!success) {
            System.out.println("获取锁1失败");
            return;
        }
        try {
            System.out.println("获取锁1成功");
            method2();
        } finally {
            System.out.println("释放锁1");
            lock.unlock();
        }
    }

    void method2() {
        RLock lock = redissonClient.getLock("lock");
        boolean success = lock.tryLock();
        if (!success) {
            System.out.println("获取锁2失败");
            return;
        }
        try {
            System.out.println("获取锁2成功");
        } finally {
            System.out.println("释放锁2");
            lock.unlock();
        }
    }
}
