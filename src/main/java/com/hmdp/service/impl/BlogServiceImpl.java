package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            getNameAndIconByblog(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        getNameAndIconByblog(blog);
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            blog.setIsLike(false); // 未登录默认未点赞
            return;
        }
        Long userId = user.getId();
        String key = "blog:liked:" + blog.getId();
        if (stringRedisTemplate.opsForZSet().score(key, userId.toString()) != null) {
            blog.setIsLike(true);
        }
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:" + id;
        Set<String> idList = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (idList == null || idList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = idList.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> users = userService.query()
                .in("id", ids)
                .last("order by field(id, " + idStr + ")")
                .list()
                        .stream()
                        .map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class))
                        .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public void updateLikeBlog(Long id) {
        //利用redis
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //查询该用户是否点赞
        String key = "blog:liked:" + id;
        Double isLiked = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if(isLiked == null){
            //未点赞，可以点赞
            //数据库点赞数+1
            boolean isSuccess =  this.update().setSql("liked = liked + 1").eq("id", id).update();
            //保存到redis
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }else{
            //已点赞，取消点赞
            boolean isSuccess = this.update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
    }

    @Override
    public Result saveBlog(Blog blog) {
        Long userId = UserHolder.getUser().getId();
        if(userId == null){
            return Result.fail("请先登录");
        }
        blog.setUserId(userId);
        //保存自己的博客
        boolean success = this.save(blog);
        if(!success){
            return Result.fail("添加失败！");
        }
        //查询这个作者的所有粉丝，封装到一个list集合里
        List<Follow> fans = followService.query().eq("follow_user_id", userId).list();
        //根据这个集合，推送给每一个粉丝推送消息
        for (Follow fan : fans){
            Long fanId = fan.getUserId();
            if(fanId == null){
                continue;
            }
            String key = "feed:" + fanId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
                if(typedTuples == null || typedTuples.isEmpty()){
                    return Result.ok();
                }
                long minTime = 0;
                int os = 1;
                List<Long> ids = new ArrayList<>(typedTuples.size());
                for (ZSetOperations.TypedTuple<String> tuple : typedTuples){
                    ids.add(Long.valueOf(tuple.getValue()));
                     long time = tuple.getScore().longValue();
                    if(time == minTime){
                        os ++;
                    }else{
                        minTime = time;
                        os = 1;
                    }
                }
                //现在已经获得了一个装着blog_id的list集合
        //根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("order by field(id, " + idStr + ")")
                .list();
        //现在封装了一个blog的集合
        //每个blog还要获取是否点赞和点赞数
        for (Blog blog : blogs) {
            isBlogLiked(blog);
            queryBlogLikes(blog.getId());
        }
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setOffset(os);
        result.setMinTime(minTime);
        return Result.ok(result);
    }

    private void getNameAndIconByblog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
