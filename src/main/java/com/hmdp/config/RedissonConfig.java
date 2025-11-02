package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        //配置类
        Config config = new Config();
        //添加Redis地址，这里添加了单点地址，也可以使用config.userClusterServers()添加集群地址
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setPassword("zhou123quan");
        return Redisson.create(config);
    }
}