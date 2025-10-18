package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryShopTypeByList() {
        //1. 从Redis中查询商铺类型
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        //2. 判断缓存是否命中
        //3 如果命中 也就是数据在Redis中存在
        if(StrUtil.isNotBlank(shopTypeListJson)){
            //将JSON数据转化为List<ShopType>集合
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //4. 如果Redis中不存在数据  则在MySQL中查询
            //这里的sort是tb_shop_type中的排序列
            //所以这段代码使用MyBatisPlus中的query方法按照sort列的顺序赋值给shopTypeList
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //5. 如果在MySQL中也没查到  返回404
        if(shopTypeList == null){
            return Result.fail("商铺列表不存在！");
        }
        //6. 如果在MySQL中查到了  将其存入Redis  使用String类型存入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopTypeList));
        //7. 返回商铺列表信息
        return Result.ok(shopTypeList);
    }

}