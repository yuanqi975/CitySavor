package com.hmdp.dto;

import lombok.Data;

@Data
public class ShopCommentCreateRequest {
    private Integer rating;
    private String content;
}
