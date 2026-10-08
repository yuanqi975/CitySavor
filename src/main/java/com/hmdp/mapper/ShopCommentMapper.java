package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.dto.ShopCommentDTO;
import com.hmdp.entity.ShopComment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ShopCommentMapper extends BaseMapper<ShopComment> {
    List<ShopCommentDTO> selectPageWithUser(@Param("shopId") Long shopId, @Param("rating") Integer rating, @Param("offset") long offset, @Param("limit") long limit);
}
