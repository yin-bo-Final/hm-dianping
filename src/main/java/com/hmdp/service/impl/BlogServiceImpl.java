package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IBlogService blogService;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        // 3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.如果未点赞，可以点赞
            // 3.1.数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2.保存用户到Redis的set集合  zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Integer id) {
        String key = BLOG_LIKED_KEY + id;
        //zrange key 0 4 查询zset 中前五个元素
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //如果是空的(可能没人点赞),直接返回一个空的集合
        if(top5 == null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //将ids使用`,`拼凑，SQL语句查询出来的结果并不是按我们期望的方式排
        //所以我们需要order by field来执行排序方式，期望的排序方式就是按照查询出来的id进行排序
        String idsStr = StrUtil.join(",",ids);
        //select * from tb_user where id in (ids[0],ids[1] ...) order by field (id,ids[0],ids[1]...)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("order by field(id," + idsStr + ")")
                .list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博客
        boolean isSuccess = blogService.save(blog);
        if (!isSuccess) {
            return Result.fail("博客保存失败");
        }
        // 3. 查询blog作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        follows.forEach(follow -> {
            //3.1 获取粉丝id
            Long userId = follow.getUserId();
            //3.2 推送笔记id给所有粉丝
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });
        // 5. 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 查询该用户收件箱（之前我们存的key是固定前缀 + 粉丝id），所以根据当前用户id就可以查询是否有关注的人发了笔记
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typeTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 10);// 一次最多10条
        //3. 非空判断
        if (typeTuples == null || typeTuples.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //4. 解析数据，blogId、minTime（时间戳）、offset，这里指定创建的list大小，可以略微提高效率，因为我们知道这个list就得是这么大
        List<Long> blogIds = new ArrayList<>(typeTuples.size());
        long minTime = 0;
        int newOffset = 1;
        for (ZSetOperations.TypedTuple<String> typeTuple : typeTuples) {
            //4.1 获取id
            String id = typeTuple.getValue();
            blogIds.add(Long.valueOf(id));

            //4.2 获取score（时间戳）
            long time = typeTuple.getScore().longValue();
            if (time == minTime){
                newOffset++;
            }else {
                minTime = time;
                newOffset = 1;
            }
        }
        // 4. 神之一手！直接用 listByIds()，它内部自动处理：
        //    - 空集合安全
        //    - 自动生成 IN (?, ?, ?)
        //    - 自动加上 ORDER BY FIELD(id, ?, ?, ?)
        List<Blog> blogs = listByIds(blogIds);

        if (CollUtil.isEmpty(blogs)) {
            return Result.ok(Collections.emptyList());
        }

        // 5. 手动按收件箱顺序排序（因为 listByIds 的 FIELD 排序可能和我们期望的顺序不完全一致）
        //    这一步保留原始时间线顺序，超级重要！
        blogs.sort(Comparator.comparingInt(b -> blogIds.indexOf(b.getId())));

        // 6. 查询用户信息 + 点赞状态（不变）
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        // 7. 封装返回
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(newOffset);

        return Result.ok(result);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
