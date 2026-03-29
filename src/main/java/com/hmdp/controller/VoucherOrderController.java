package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }


    // 压测隔离接口，全局放行，不需要登录校验
    @PostMapping("/bench")
    public Result benchSeckill() {
        // 固定用券2
        long voucherId = 2;
        // 自动生成不重复用户
        long userId = System.nanoTime();

        // 自动初始化库存
        stringRedisTemplate.opsForValue().setIfAbsent("seckill:stock:" + voucherId, "100");

        // 模拟登录
        UserDTO userDTO = new UserDTO();
        userDTO.setId(userId);
        UserHolder.saveUser(userDTO);

        // 执行秒杀
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
