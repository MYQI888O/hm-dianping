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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
        //stringRedisTemplate.expire("login:token:"+token, 30, TimeUnit.MINUTES);
        stringRedisTemplate.persist("login:token:"+token);

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //实现用户签到功能
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.获取当前时间（月份）
        LocalDateTime now = LocalDateTime.now();
        //3.把时间变为key
        String time = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:" + userId + ":" + time;
        //4.获取今日具体是第几天
        int day = now.getDayOfMonth();
        //5.填入redis
        stringRedisTemplate.opsForValue().setBit(key, day - 1,  true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //实现统计用户连续签到功能
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.获取当前时间（月份）
        LocalDateTime now = LocalDateTime.now();
        //3.把时间变为key
        String time = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = "sign:" + userId + ":" + time;
        //4.获取今日具体是第几天
        int day = now.getDayOfMonth();
        List<Long> bitField = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if (bitField == null || bitField.isEmpty()){
            return Result.ok();
        }
        Long signDays = bitField.get(0);
        if (signDays == null || signDays == 0){
            return Result.ok();
        }
        int count = 0;
        while (true){
            if((signDays & 1) == 0){
                break;
            }else {
                count ++;
            }
            signDays = signDays >> 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
