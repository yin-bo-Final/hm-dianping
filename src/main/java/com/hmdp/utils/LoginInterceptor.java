package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;


//对象是手动new出来的 不受Spring管理  没有@Component注解  所以不能使用@Resource来注入StringRedisTemplate
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 从request获取请求头中的token
        //前端代码中看到请求头叫authorization
        String token = request.getHeader("authorization");

        //使用StrUtil来判断token是否为空
        //如果为空 拦截
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }
        //2. 利用token从redis中获取用户信息
        //使用entries获取的是HashMap
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        log.debug(userMap.toString());


        //3. 判断用户是否存在
        if (userMap.isEmpty()) {
            //4. 不存在，则拦截
            response.setStatus(401);
            return false;
        }

        //5. 将查询到的Hash数据转为userDTO对象  这样才能存到ThreadLocal当中
        // 这里使用BeanUtil的fillBeanWithMap方法  第三个参数是是否忽略错误  填false
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);

        //6. 刷新token的有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //7. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}