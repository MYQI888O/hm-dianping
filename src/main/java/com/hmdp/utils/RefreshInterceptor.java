package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;
    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

           String token = request.getHeader("authorization");
           if(token == null){
               return true;
           }

         Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries("login:token:"+token);
         if(userMap.isEmpty()){
             return true;
         }
         UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(userMap, userDTO);
        UserHolder.saveUser(userDTO);

        stringRedisTemplate.expire("login:token:"+token, 30, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
