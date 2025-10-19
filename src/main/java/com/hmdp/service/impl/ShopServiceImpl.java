package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service

public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate  stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //1. 从Redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 判断缓存是否命中
                //3 如果命中 也就是数据在Redis中存在
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //3. 如果Redis中不存在数据  则在MySQL中查询
        Shop shop = getById(id);
        //4. 如果在MySQL中也没查到  返回404
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        //5. 如果在MySQL中查到了 存入Redis 使用String存的是JSON类型
        //写入缓存设置TTL
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6. 返回商铺信息
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
