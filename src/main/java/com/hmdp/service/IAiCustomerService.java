package com.hmdp.service;

import com.hmdp.dto.AiChatMessageDTO;
import com.hmdp.dto.AiChatMessageRequest;
import com.hmdp.dto.AiChatResponse;

import java.util.List;

public interface IAiCustomerService {
    String createConversation(Long userId);

    List<AiChatMessageDTO> getHistory(String conversationId, Long userId);

    AiChatResponse sendMessage(String conversationId, Long userId, AiChatMessageRequest request);
}
