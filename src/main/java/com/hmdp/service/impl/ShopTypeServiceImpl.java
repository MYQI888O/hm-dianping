package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryList() {
        //查询redis
        String shopTypeCache =  stringRedisTemplate.opsForValue().get("cache:shop:type:");
        //如果redis存在，直接返回
        if(shopTypeCache != null){
            List<ShopType> shopTypeList = JSON.parseArray(shopTypeCache, ShopType.class);
            return shopTypeList;
        }else{
            List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();
            if(shopTypeList != null){
                stringRedisTemplate.opsForValue().set("cache:shop:type:", JSON.toJSONString(shopTypeList));
                return shopTypeList;
            }else{
                return new ArrayList<>();
            }
        }
    }
}
