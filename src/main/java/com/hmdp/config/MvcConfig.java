package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;


//这个类加了@Configuration  说明这个类由Spring构建  所以可以用@Resource注解
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;
    @Resource
    private RefreshTokenInterceptor refreshTokenInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //第一个拦截器  拦截所有请求  有用户数据刷新token有效期  没有用户数据不刷新直接放行
        //使用order来规定拦截器顺序
        registry.addInterceptor(refreshTokenInterceptor).order(0);


        //第二个拦截器  除了以下路径如果没登录直接拦截
        registry.addInterceptor(loginInterceptor).order(1)
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                );


    }
}