package com.hmdp.ai;

import java.util.Map;

public interface AiTool {
    String name();

    String description();

    Map<String, Object> parameters();

    String execute(Map<String, Object> arguments, Long userId);
}
