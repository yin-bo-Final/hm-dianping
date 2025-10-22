package com.hmdp.utils;

import org.apache.tomcat.jni.Local;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //开始时间
    private static final  Long BEGIN_TIMESTAMP = 1735689600L;
    //序列号位数
    public static final Long COUNT_BITS = 32L;

    public long nextId(String keyPrefix){
        //1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long currentTime = now.toEpochSecond(ZoneOffset.UTC);

        //timeStamp为时间戳
        long timeStamp =  currentTime - BEGIN_TIMESTAMP;


        //2. 生成序列号
        //2.1 获取当前日期  精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 自增长
            //不会担心空指针问题  他会自增长
        String key = "icr:"+keyPrefix+":"+date;
        long count = stringRedisTemplate.opsForValue().increment(key);

        //3. 拼接并返回
            //拼接long类型用位运算
            //让timeStamp向左移动32位
            //用或运算将count填充到timeStamp
        return  timeStamp << COUNT_BITS|count;
    }



    //获取项目的初始时间 这里定为2025年1月1号0点0分
    public static void main(String[] args) {

        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 0, 0);
        //将时间转化为秒
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = "+second);
        //这里输出是1735689600 说明项目初识时间是这个时间  将这个时间定义为常量
    }
}
