package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service

public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate  stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //因为要传函数  所以用lambda表达式是id -> getById(id)  这里简写this::getById
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //逻辑过期解决缓存击穿
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //判断是否为空数据
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //返回商铺信息
        return Result.ok(shop);
    }




    @Override
    //如果删除缓存抛出异常  依赖@Transactional来回滚数据库事务
    @Transactional
    public Result update(Shop shop) {
        //1. 更新数据库
            //先判断以下ID是否为空
        if(shop.getId()==null){
            return Result.fail("店铺id不能为空");
        }
            //更新数据库
        updateById(shop);
        //2. 删除缓存  如果有之前的商铺id 这里直接删除之前的商铺的缓存  保持原子性
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        //返回结果
        return Result.ok();
    }
}
