package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.ShopCommentCreateRequest;
import com.hmdp.dto.ShopCommentPageDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopComment;

import java.util.List;
import com.hmdp.dto.ShopCommentDTO;

public interface IShopCommentService extends IService<ShopComment> {
    Result queryComments(Long shopId, int current, int pageSize);

    Result createComment(Long shopId, Long userId, ShopCommentCreateRequest request);

    ShopCommentPageDTO getCommentPage(Long shopId, int current, int pageSize);

    List<ShopCommentDTO> getRecentComments(Long shopId, Integer rating, int limit);
}
