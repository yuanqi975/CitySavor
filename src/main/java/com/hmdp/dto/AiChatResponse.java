package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiChatResponse {
    private String conversationId;
    private String messageId;
    private String content;
    private Long createdAt;
}
