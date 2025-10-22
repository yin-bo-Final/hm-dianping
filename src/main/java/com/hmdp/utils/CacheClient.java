package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;



    //方法1：将任意Java对象序列化为JSON，并存储到String类型的Key中，并可以设置TTL过期时间
    public void set(String key, Object value,Long time, TimeUnit timeUnit) {
        //将value序列化为JSON字符串
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }


    //方法2：将任意Java对象序列化为JSON，并存储在String类型的Key中，并可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        //由于要生成过期时间 所以需要用到RedisData
        RedisData redisData = new RedisData();
        //添加逻辑过期时间
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //添加数据
        redisData.setData(value);
        //将数据转化成json存入redis 因为是逻辑过期所以不需要加过期时间
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }



    //方法3：根据指定的Key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    //这里返回值不确定 使用泛型
    //所以参数要有数据的类型 来告诉泛型要用什么类型
    //因为id是一个string类型 所以这里需要传一个id的前缀
    //id的类型也不确定  所以也需要用泛型
    //因为不知道类型 不能使用getById 所以使用function 传入数据库取出操作 第一个参数是传入的值  第二个参数是返回类型也就是R
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }


        if(json != null){
            return null;
        }


        R r = dbFallback.apply(id);


        //如果数据库中没有  返回空值
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.SECONDS);
            return null;
        }

        set(key,r,time,timeUnit);

        return r;
    }



    //方法4：根据指定的Key查询缓存，并反序列化为指定类型，需要利用互斥锁解决缓存击穿问题
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //如果不为空（查询到了），则转为Shop类型直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        R r = null;
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            //否则去数据库中查
            boolean flag = tryLock(lockKey);
            if (!flag) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            r = dbFallback.apply(id);
            //查不到，则将空值写入Redis
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //并存入redis，设置TTL
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return r;
    }



    //创建一个缓存重建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //5. 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit timeUnit) {

        //1. 从Redis查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //因为这是解决缓存穿透的代码，默认已经有了热点数据，所以如果未命中直接返回空
        if(StrUtil.isBlank(json)){
            return null;
        }

        //如果命中  判断缓存是否过期
        //先把JSON反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //将data转为Shop对象

        //redisData中的data是一个JSONObject类型
        //将data修改为shop类型
        JSONObject data = (JSONObject)redisData.getData();
        //这里R是泛型  所以不能用R.class
        R r = JSONUtil.toBean(data, type);

        //获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        //未过期 直接返回商铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        //过期了 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean getKey = tryLock(lockKey);
        if (getKey) {
            //成功获取互斥锁  开启独立线程  实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //缓存重建就是重新查看数据库  重新将数据写入缓存
                    //先查数据库 得到数据
                    //r1是新的数据
                    R r1 = dbFallback.apply(id);
                    //再写Redis
                    //这里将ri变为之后的r
                    setWithLogicExpire(key,r1,time,timeUnit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //这里如果没有获取互斥锁  获取的是过期的数据
        return r;
    }


    //设置互斥锁
    private boolean tryLock(String key){
        //setOfAbsent就是redis中的setnx 如果value不为空则不能修改
        Boolean Mutex = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_SHOP_KEY + key, "notnull", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        //避免返回值为null，我们这里使用了BooleanUtil工具类
        return BooleanUtil.isTrue(Mutex);
    }


    //释放互斥锁
    private void unLock(String key){
        stringRedisTemplate.delete(LOCK_SHOP_KEY + key);
    }

}
