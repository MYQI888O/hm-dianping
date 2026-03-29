package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MVCConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private LoginInterceptor loginInterceptor; // 注入Spring容器的实例
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        //登录拦截器，只负责管登录
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**",
                        "/voucher-order/bench/**").order(1);

        //刷新拦截器，只负责管刷新
        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
