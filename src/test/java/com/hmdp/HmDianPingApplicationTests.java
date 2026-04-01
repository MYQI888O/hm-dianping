package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void loadShop(){
        //1.查询所有店铺的信息
        List<Shop> list = shopService.list();
        //2.根据店铺typeId来给所有店铺分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.在每一个组里，写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1 获取店铺typeId
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;

            //3.2 获取店铺list的信息并封装一个redis类 RedisGeoCommands.GeoLocation<String>
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            //3.3取出每一个店铺，存到RedisGeoCommands.GeoLocation<String>
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }







        //把这个Location泛型存到redis中




    }


}
