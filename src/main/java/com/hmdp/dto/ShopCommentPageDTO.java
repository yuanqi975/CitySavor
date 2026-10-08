package com.hmdp.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ShopCommentPageDTO {
    private List<ShopCommentDTO> records;
    private Long total;
    private Double averageRating;
    private Map<Integer, Long> ratingDistribution;
}
