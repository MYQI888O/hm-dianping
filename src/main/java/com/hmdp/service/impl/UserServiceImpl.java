package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        Boolean validPhone = RegexUtils.isPhoneInvalid( phone);
        if (validPhone) {
            //手机号格式错误
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //把验证码发送到redis里
        stringRedisTemplate.opsForValue().set("login:code:" + phone, code, 2, TimeUnit.MINUTES);
        //发送验证码（用日志实现）
        log.info("验证码是{}",  code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        if (ObjectUtils.isEmpty(phone) || ObjectUtils.isEmpty(code)) {
            return Result.fail("手机号或验证码不能为空");
        }
        //校验手机号
        Boolean validPhone = RegexUtils.isPhoneInvalid(phone);
        if (validPhone) {
            //手机号格式错误
            return Result.fail("手机号格式错误");
        }
        //在redis里进行校验
        String cacheCode = stringRedisTemplate.opsForValue().get("login:code:" + phone);
        // 4. 校验验证码和手机号（无NPE风险）
        if (ObjectUtils.isEmpty(cacheCode) || !Objects.equals(code, cacheCode)) {
            return Result.fail("验证码错误");
        }

        //根据手机号查询用户
        //mybatisPlus的查询功能
        //判断用户是否存在
        User user = this.query().eq("phone", phone).one();
        if (user == null){
            //如果不存在则返回到数据库里
            user =  createUserWithPhone(phone);
        }
        //把用户信息保存在redis里
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        //这里是把userDTO这个整体变为一个map
        Map<String, Object> userMap = BeanUtil.beanToMap( userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll("login:token:"+token, userMap);
        stringRedisTemplate.expire("login:token:"+token, 30, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
