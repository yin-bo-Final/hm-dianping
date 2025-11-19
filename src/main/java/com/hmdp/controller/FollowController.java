package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.BlogServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
    @Resource
    private IBlogService blogService;

    //判断当前用户是否关注了该博主
    //follow/or/not 就是关注没有
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    //实现取关/关注
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFellow) {
        return followService.follow(followUserId,isFellow);
    }


    @GetMapping("/common/{id}")
    public Result getCommonFollow(@PathVariable("id") Long id) {
        return followService.getCommonFollow(id);
    }
}
