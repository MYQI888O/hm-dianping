package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");

        String redisKey = "login:token:" + token;

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(redisKey);


        UserDTO userDTO = new UserDTO();
        // 从userMap中取id并赋值（关键！之前完全没这步）
        Object idObj = userMap.get("id");
        if (idObj != null) {
            userDTO.setId(Long.valueOf(idObj.toString())); // 转成Long类型
        }
        // 赋值其他字段
        userDTO.setNickName((String) userMap.get("nickName"));
        userDTO.setIcon((String) userMap.get("icon"));
        // ========== 核心缺失步骤：把UserDTO存UserHolder ==========
        UserHolder.saveUser(userDTO);

        //只判断是不是要拦截
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
