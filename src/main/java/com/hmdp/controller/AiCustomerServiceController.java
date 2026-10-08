package com.hmdp.controller;

import com.hmdp.dto.AiChatMessageDTO;
import com.hmdp.dto.AiChatMessageRequest;
import com.hmdp.dto.AiChatResponse;
import com.hmdp.dto.Result;
import com.hmdp.service.IAiCustomerService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/ai/customer-service/conversations")
public class AiCustomerServiceController {
    @Resource
    private IAiCustomerService aiCustomerService;

    @PostMapping
    public Result createConversation() {
        return Result.ok(aiCustomerService.createConversation(UserHolder.getUser().getId()));
    }

    @GetMapping("/{conversationId}/messages")
    public Result getHistory(@PathVariable String conversationId) {
        List<AiChatMessageDTO> messages = aiCustomerService.getHistory(conversationId, UserHolder.getUser().getId());
        return Result.ok(messages);
    }

    @PostMapping("/{conversationId}/messages")
    public Result sendMessage(@PathVariable String conversationId, @RequestBody AiChatMessageRequest request) {
        AiChatResponse response = aiCustomerService.sendMessage(conversationId, UserHolder.getUser().getId(), request);
        return Result.ok(response);
    }
}
