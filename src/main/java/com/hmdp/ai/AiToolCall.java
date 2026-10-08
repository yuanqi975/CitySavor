package com.hmdp.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiToolCall {
    private String id;
    private String name;
    private String arguments;
}
