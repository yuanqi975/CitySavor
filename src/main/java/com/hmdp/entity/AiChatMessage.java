package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ai_chat_message")
public class AiChatMessage {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String conversationId;
    private Long userId;
    private String role;
    private String content;
    private String requestId;
    private String toolName;
    private String toolArguments;
    private String status;
    private LocalDateTime createTime;
}
