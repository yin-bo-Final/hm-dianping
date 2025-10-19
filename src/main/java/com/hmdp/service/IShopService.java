package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;



public interface IShopService extends IService<Shop> {


    //先从Redis中查  这里的常量值是固定的前缀+商铺id

    Result queryById(Long id);

    Result update(Shop shop);
}
