package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
            boolean isSuccess =  this.update().setSql("liked = liked + 1").eq("id", id).gt("liked", 0).update();
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

    private void getNameAndIconByblog(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
