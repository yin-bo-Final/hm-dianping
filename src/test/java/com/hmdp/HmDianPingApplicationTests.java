package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;

import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test
    void testGenerateId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = "+id);
            }
            latch.countDown();
        };
        long beginTime = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long endTime = System.currentTimeMillis();
        System.out.println("time = " + (endTime - beginTime));
    }



    @Test
    void testSaveShopToRedis() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY + 1L,shop,2L,TimeUnit.SECONDS);
    }


}
