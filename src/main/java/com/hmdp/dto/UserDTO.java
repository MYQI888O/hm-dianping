package com.hmdp.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
