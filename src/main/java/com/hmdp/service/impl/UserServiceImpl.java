package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    //这里的Result是自己定义的类
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        //这里黑马已经写好了手机号正则表达式工具
        //RegexPatterns  RegexUtils
        if(RegexUtils.isPhoneInvalid(phone)){
            //2. 如果不符合 返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3. 符合 生成验证码
        //这里直接用RandomUtil里的随机生成数字方法
        String code = RandomUtil.randomNumbers(6);

        //4. 将验证码保存到session
        //使用setAttribute方法来将验证码保存到session
        session.setAttribute("code",code);

        //5. 发送验证码
        //这里是模拟  实际上线需要替换
        log.debug("发送短信验证码成功，验证码：{}",code);

        //返回OK
        return Result.ok();
    }
}
