package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.apache.tomcat.util.buf.UDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFellow) {
        //1. 获取登录的用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //2. 判断到底是关注还是取关
        if (isFellow) {
            //3. 关注：新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //3.1 把关注目标存到Redis中 sadd userId followUserId

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        } else {
            //4. 取关：删除数据  delete from tb_follow where user_id = ? and follow_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId)
            );
            //4.1 从redis中移除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }



    @Override
    public Result isFollow(Long followUserId) {
        //1. 获取用户信息
        Long userId = UserHolder.getUser().getId();

        //2. 查询是否关注 select * from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //3. 判断是否关注
        return Result.ok(count > 0);
    }

    @Override
    public Result getCommonFollow(Long id) {
        //1. 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + id;
        String key2 = "follows:" + userId;
        //对当前用户和博主用户的关注列表取交集   使用intersect
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //判断是否有共同关注
        if (intersect == null||intersect.isEmpty()) {
            //无交集，返回空集合
            return Result.ok(Collections.emptyList());
        }

        //有共同关注，将结果转换为List
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据ids去查询共同关注用户，封装成UserDTO再返回
        List<UserDTO> userDTOS = userService
                .listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

}