package com.hmdp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleChatModelClient implements AiChatModelClient {
    @Value("${ai.enabled:false}")
    private boolean enabled;
    @Value("${ai.base-url:}")
    private String baseUrl;
    @Value("${ai.api-key:}")
    private String apiKey;
    @Value("${ai.model:}")
    private String model;

    @Resource
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public AiModelResponse complete(List<Map<String, Object>> messages, List<AiTool> tools) {
        if (!enabled || isBlank(baseUrl) || isBlank(apiKey) || isBlank(model)) {
            throw new AiCustomerServiceException("客服 AI 尚未配置，请联系管理员设置 AI_ENABLED、AI_BASE_URL、AI_API_KEY 和 AI_MODEL");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", toolDefinitions(tools));
        body.put("tool_choice", "auto");
        body.put("temperature", 0.2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizeBaseUrl(baseUrl) + "/chat/completions",
                    new HttpEntity<>(body, headers), String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            throw new AiCustomerServiceException("客服 AI 暂时不可用，请稍后再试", e);
        }
    }

    private List<Map<String, Object>> toolDefinitions(List<AiTool> tools) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (AiTool tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", "function");
            definition.put("function", function);
            definitions.add(definition);
        }
        return definitions;
    }

    private AiModelResponse parseResponse(String body) throws Exception {
        JsonNode message = objectMapper.readTree(body).path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new AiCustomerServiceException("客服 AI 返回了无效响应");
        }
        List<AiToolCall> toolCalls = new ArrayList<>();
        for (JsonNode node : message.path("tool_calls")) {
            JsonNode function = node.path("function");
            toolCalls.add(new AiToolCall(node.path("id").asText(), function.path("name").asText(), function.path("arguments").asText("{}")));
        }
        return new AiModelResponse(message.path("content").isNull() ? "" : message.path("content").asText(), toolCalls);
    }

    private String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
