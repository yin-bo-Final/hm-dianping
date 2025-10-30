package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    //查询秒杀全ID 所以要注入秒杀券信息
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1. 查询秒杀券
            //因为优惠券和秒杀券ID相同 所以这里可以直接使用优惠券ID
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2. 判断秒杀是否开始
            //2.1 没有开始 返回错误信息
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动没有开始");
        }

        //3. 判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束");
        }

        //4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("该优惠券已售罄");
        }





        //再这里加同步锁就是先获取锁，再执行createVoucherOrder函数去完成下单函数
        //等函数执行完也就是事务提交了，这时再释放锁
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //这里代理对象是这个接口的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            //要调用代理的createVoucherOrder,要在接口中创建这个函数
            return proxy.createVoucherOrder(voucherId);
        }
    }


    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5. 判断用户是否重复下单
        //从ThreadLocal中获得用户id
        Long userId = UserHolder.getUser().getId();


            //5.1 根据userId和voucherId来查询订单
            Integer count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId).count();


            //5.2 判断是否存在
            //如果存在
            if (count > 0) {
                return Result.fail("您已经抢购过该秒杀券!");
            }


            //6. 扣减库存
            boolean success = seckillVoucherService.update()        // 1. 开始更新操作
                    .setSql("stock = stock - 1")                    // 2. 设置更新的SQL片段  set stock = stock - 1
                    .eq("voucher_id", voucherId)            // 3. WHERE条件：指定优惠券ID  where voucher_id = voucherId
                    .gt("stock", 0)                    // 4. WHERE条件：库存必须大于0      where stock > 0
                    .update();                                      // 5. 执行更新，返回是否成功

            if (!success) {                                         // 6. 判断更新结果
                return Result.fail("库存不足");
            }


            //7. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();

            //设置订单id
            Long orderId = redisIdWorker.nextId("order");
            //设置代金券id
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);

            //8. 将订单保存到数据库
            save(voucherOrder);

            //9. 返回订单ID
            return Result.ok(orderId);
        }
}
