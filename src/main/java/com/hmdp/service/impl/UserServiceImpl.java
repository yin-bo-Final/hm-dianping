package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    //完成Redis注解
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    //实现发送验证码功能
    @Override
    //这里的Result是自己定义的类
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        //这里黑马已经写好了手机号正则表达式工具
        //RegexPatterns  RegexUtils
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2. 如果不符合 返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3. 符合 生成验证码
        //这里直接用RandomUtil里的随机生成数字方法
        String code = RandomUtil.randomNumbers(6);

        //4. 将验证码保存到Redis
        //这里的key使用login:code:手机号 防止与其他业务的key冲突
        //key需要有效期  设置成两分钟  2和login:code最好定义个常量类  看起来更专业
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5. 发送验证码
        //这里是模拟  实际上线需要替换
        log.debug("发送短信验证码成功，验证码：{}", code);

        //返回OK
        return Result.ok();
    }



    //实现登录功能
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //2. 校验验证码
        // 从Redis获取验证码并校验
        //注意这 opsForValue().get(LOGIN_CODE_KEY + phone);  phone是发送验证码之后的phone  所以不需要二次验证
        //如果phone前后不一致  cacheCode就不能获取数据 就是null
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        //3. 不一致 直接报错
        if (cacheCode == null) {
            return Result.fail("验证码已过期或填写手机号不一致");
        } else if (!code.equals(cacheCode)) {
            return Result.fail("验证码错误");
        }


        //4. 如果一致
        //可以将redis中的code删除了
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 根据手机号查询用户
        //使用的是mybatisplus 所以很简单就可以实现查询
        //本类中继承了ServiceImpl父类 这个类是mybatisplus提供的
        //这个类可以实现对于单表的增删改查
        //使用方法是表明实体类和Mapper是什么
        //query()就等同于SQL里的 select * from tb_user where
        User user = query().eq("phone", phone).one();

        //5. 判断用户是否存在
        if (user == null) {
            //6. 不存在 创建新用户 保存用户到数据库
            user = createUserWithPhone(phone);
        }


        //7. 保存用户信息到redis中
        //7.1 随机生成token  作为登录令牌 使用UUID作为token
        String token = UUID.randomUUID().toString();

        //7.2 将user对象转化为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        HashMap<String, String> userMap = new HashMap<>();

        // 添加空值检查，避免存储null字符串
        if (userDTO.getIcon() != null) {
            userMap.put("icon", userDTO.getIcon());
        }
        userMap.put("id", String.valueOf(userDTO.getId()));
        if (userDTO.getNickName() != null) {
            userMap.put("nickName", userDTO.getNickName());
        }
        //7.3 存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //用户信息一直存到redis会造成redis臃肿  所以也需要给登录信息设置有效期
        //在存储的时候不能直接设置有效期  需要使用 expire方法
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8. 返回给客户端
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天(1~31)
        int dayOfMonth = now.getDayOfMonth();
        //5. 写入Redis  BITSET key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 获取日期
        LocalDateTime now = LocalDateTime.now();
        //3. 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4. 获取今天是当月第几天
        int dayOfMonth = now.getDayOfMonth();
        //5. 获取截止到今天的签到记录 返回的是一个十进制的数字  BITFIELD key GET uDay 0
            //BITFIELD key GET uDay 0
            //uDay代表查询的是前Day位     最后的0是offset代表从第一位开始
            //例如今天第14天  就是u14 0   意思是从第1位开始查询到第14位
        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType
                                .unsigned(dayOfMonth))
                        .valueAt(0)
                );
        if (result == null||result.isEmpty()) {
            return Result.ok(0);
        }
        //6. 遍历循环
        int cnt = 0;
        Long num = result.get(0);
        while (true){
            //7. 让数字于1做与运算，得到数字最后一个bit位
            if((num &1) == 0){ //如果bit位为0 证明未签到 连续签到中断
                break;
            }else {
                cnt++;
                //数字右移 抛弃最后一位
                //使用>>>而不是>>是因为
                    //>>会符号位扩展，导致死循环
                    //>>>无符号右移
                num >>>= 1;
            }

        }
        return Result.ok(cnt);
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

