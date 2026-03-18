package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryShopById(Long id) {
        //检查是不是在redis里
        String shopCache = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //存在，直接返回
        if(shopCache != null){
            return Result.ok(shopCache);
        }else{
            //如果不存在，查询数据库
            Shop shop = getById(id);
            //如果数据库也不存在，直接返回错误
            if(shop == null){
                return Result.fail("店铺不存在");
            }else{
                String shopJson = JSONUtil.toJsonStr(shop);
                stringRedisTemplate.opsForValue().set("cache:shop:" + id, shopJson);
                return Result.ok(shopJson);
            }
        }
    }
}
