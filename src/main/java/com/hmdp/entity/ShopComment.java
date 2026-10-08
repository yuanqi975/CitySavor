package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_shop_comment")
public class ShopComment {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private Integer rating;
    private String content;
    private Integer isMock;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
