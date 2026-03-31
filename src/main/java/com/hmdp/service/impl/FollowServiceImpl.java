package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;


    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:user:" + userId;
        if(isFollow){
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            save(follow);

            stringRedisTemplate.opsForZSet().add(
                    key,
                    followUserId.toString(),
                    System.currentTimeMillis()
            );
        }else{
            remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));

            stringRedisTemplate.opsForZSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:user:" + userId;
        Double score = stringRedisTemplate.opsForZSet().score(key, followUserId.toString());
        return Result.ok(score != null);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follow:user:" + userId;
        String key2 = "follow:user:" + id;
        String tempKey = "temp:intersect:" + userId + ":" + id;

// 3. 计算 ZSet 交集，存入临时 key
        stringRedisTemplate.opsForZSet().intersectAndStore(key, key2, tempKey);
// 4. 从临时 key 读取所有结果（就是共同关注）
        Set<String> list = stringRedisTemplate.opsForZSet().range(tempKey, 0, -1);
        if(list == null || list.isEmpty()){
            return Result.ok(0);
        }
        //获取set集合里的每一个id值
        List<Long> ids = list.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        stringRedisTemplate.delete(tempKey);
        return Result.ok(users);
    }
}
