package com.hmdp.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiModelResponse {
    private String content;
    private List<AiToolCall> toolCalls;
}
