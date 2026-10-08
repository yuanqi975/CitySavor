package com.hmdp.dto;

import lombok.Data;

@Data
public class ShopCommentDTO {
    private Long id;
    private Long userId;
    private String nickName;
    private String icon;
    private Integer rating;
    private String content;
    private Long createdAt;
}
