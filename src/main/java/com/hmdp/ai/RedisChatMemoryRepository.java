package com.hmdp.ai;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.AiChatMessageDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.AI_CHAT_CONTEXT_KEY;
import static com.hmdp.utils.RedisConstants.AI_CHAT_CONTEXT_MESSAGE_LIMIT;
import static com.hmdp.utils.RedisConstants.AI_CHAT_CONTEXT_TTL_DAYS;

@Repository
public class RedisChatMemoryRepository {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public List<AiChatMessageDTO> getRecent(Long userId, String conversationId) {
        List<String> values = stringRedisTemplate.opsForList().range(key(userId, conversationId), 0, -1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiChatMessageDTO> messages = new ArrayList<>();
        for (String value : values) {
            messages.add(JSONUtil.toBean(value, AiChatMessageDTO.class));
        }
        return messages;
    }

    public void append(Long userId, String conversationId, AiChatMessageDTO message) {
        String key = key(userId, conversationId);
        stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(message));
        stringRedisTemplate.opsForList().trim(key, -AI_CHAT_CONTEXT_MESSAGE_LIMIT, -1);
        stringRedisTemplate.expire(key, AI_CHAT_CONTEXT_TTL_DAYS, TimeUnit.DAYS);
    }

    private String key(Long userId, String conversationId) {
        return AI_CHAT_CONTEXT_KEY + userId + ":" + conversationId;
    }
}
