package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        //创建单线程的定时任务线程池
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private boolean lockInRedis(String key){
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //拆包，把Boolean变为boolean
        return BooleanUtil.isTrue(isLock);
    }
    private void unLockInRedis(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result queryShopById(Long id) {
                Shop shop  = queryShopMutex(id);
                return shop == null ? Result.fail("店铺不存在") : Result.ok(shop);
    }
    //缓存击穿
    public Shop queryShopMutex(Long id) {
        //检查是不是在redis里
        String shopCache = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //存在，直接返回
        if(StrUtil.isNotBlank(shopCache)){
            //同时满足不为“”和不为null
            Shop shop = JSONUtil.toBean(shopCache, Shop.class);
            return shop;
        }
        if(shopCache != null){
            //这里的意思是shopCache为空，也就是'',所以是空缓存
            return null;
        }
        //开始缓存重建
        String lock = "lock:shop:" + id;
        Shop shop = null;
        try {
            int retryCount = 0;
            final int MAX_RETRY = 3; // 最多重试3次
                    // 替换递归为循环
            while (!lockInRedis(lock) && retryCount < MAX_RETRY) {
                retryCount++;
                Thread.sleep(50);
            }
            // 重试次数用完仍拿不到锁，直接返回null
            if (retryCount >= MAX_RETRY) {
                return null;
            }
            //获取了锁，再次检测redis是否存在，如果存在不用重建
            String shopCache2 = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
            if(StrUtil.isNotBlank(shopCache2)){
                //同时满足不为“”和不为null
                Shop shop2 = JSONUtil.toBean(shopCache2, Shop.class);
                return shop2;
            }
            //如果不存在，查询数据库
            shop = getById(id);
            //如果数据库也不存在，存入空值
            if(shop == null){
                stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", 1, TimeUnit.MINUTES);
                return null;
            }
            //数据库存在，但是redis不存在，把数据放到redis里
            String shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, shopJson, 30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLockInRedis(lock);
        }

        return shop;
    }

    @Override
    public Result updateByIdIntoRedis(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("店铺id不能为空");
        }
        //运用延迟双删原理

        //第一步 删除缓存
        stringRedisTemplate.delete("cache:shop:" + shop.getId());
        //第二步，更改数据库
        this.updateById(shop);
        //第三步，再次删除缓存防止多线程引发的脏读(异步删除，不能堵塞进程）
        scheduler.schedule(() -> {
            stringRedisTemplate.delete("cache:shop:" + shop.getId());
        }, 2, TimeUnit.SECONDS);

        return Result.ok();
    }
}
