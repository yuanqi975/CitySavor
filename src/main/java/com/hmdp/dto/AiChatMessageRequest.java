package com.hmdp.dto;

import lombok.Data;

@Data
public class AiChatMessageRequest {
    private String content;
    private String requestId;
}
