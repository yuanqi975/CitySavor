package com.hmdp.ai;

import java.util.List;
import java.util.Map;

public interface AiChatModelClient {
    AiModelResponse complete(List<Map<String, Object>> messages, List<AiTool> tools);
}
