package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.ShopCommentCreateRequest;

public final class ShopCommentValidator {
    private ShopCommentValidator() {
    }

    public static void validate(ShopCommentCreateRequest request) {
        if (request == null || request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("评分必须在 1 到 5 星之间");
        }
        if (StrUtil.isBlank(request.getContent()) || request.getContent().trim().length() > 500) {
            throw new IllegalArgumentException("评论内容长度必须在 1 到 500 个字符之间");
        }
    }
}
