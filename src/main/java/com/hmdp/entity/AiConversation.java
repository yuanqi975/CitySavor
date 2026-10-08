package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ai_conversation")
public class AiConversation {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private Long userId;
    private String title;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
