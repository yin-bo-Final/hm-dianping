package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    //实现发送验证码功能
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
        session.setAttribute("phone",phone);
        //5. 发送验证码
        //这里是模拟  实际上线需要替换
        log.debug("发送短信验证码成功，验证码：{}",code);

        //返回OK
        return Result.ok();
    }



    //实现登录功能
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //2. 校验验证码
        // 从session中取出验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();

        //3. 不一致 直接报错
        if(cacheCode == null){
            return Result.fail("验证码已过期");
        }
        else if(!code.equals(cacheCode.toString())){
            return  Result.fail("验证码错误");
        }

        //另外加的 黑马没讲的
        //如果用户用一个手机号来请求验证码 有了验证码之后将手机号改了 会出现新建用户的漏洞
        //这里来保证申请验证码的手机号和提交的手机号一致
        if(!session.getAttribute("phone").equals(phone)){
            return Result.fail("申请验证码的手机号和当前手机号不符");
        }
        session.removeAttribute("code");
        session.removeAttribute("phone");
        //4. 如果一致 根据手机号查询用户
            //使用的是mybatisplus 所以很简单就可以实现查询
            //本类中继承了ServiceImpl父类 这个类是mybatisplus提供的
            //这个类可以实现对于单表的增删改查
            //使用方法是表明实体类和Mapper是什么
            //query()就等同于SQL里的 select * from tb_user where
        User user = query().eq("phone", phone).one();
        //5. 判断用户是否存在
        if(user == null){
            //6. 不存在 创建新用户 保存用户到数据库
            user = createUserWithPhone(phone);
        }
        //7. 存在
        //8. 保存用户到session
        session.setAttribute("user",user);
        //session的原理是cookie 所以不需要返回登陆凭证
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        //创建新的用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(7));
        //使用mybatisplus保存用户到数据库
        save(user);
        return user;
    }
}

