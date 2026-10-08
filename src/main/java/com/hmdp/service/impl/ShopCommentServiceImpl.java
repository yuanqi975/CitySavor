package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopCommentCreateRequest;
import com.hmdp.dto.ShopCommentPageDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopComment;
import com.hmdp.mapper.ShopCommentMapper;
import com.hmdp.service.IShopCommentService;
import com.hmdp.service.IShopService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ShopCommentServiceImpl extends ServiceImpl<ShopCommentMapper, ShopComment> implements IShopCommentService {
    private static final int MAX_PAGE_SIZE = 20;

    @Resource
    private IShopService shopService;

    @Override
    public Result queryComments(Long shopId, int current, int pageSize) {
        return Result.ok(getCommentPage(shopId, current, pageSize));
    }

    @Override
    public ShopCommentPageDTO getCommentPage(Long shopId, int current, int pageSize) {
        int safeCurrent = Math.max(current, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long total = count(visibleComments(shopId));
        ShopCommentPageDTO page = new ShopCommentPageDTO();
        page.setRecords(getBaseMapper().selectPageWithUser(shopId, null, (long) (safeCurrent - 1) * safePageSize, safePageSize));
        page.setTotal(total);
        page.setRatingDistribution(ratingDistribution(shopId));
        page.setAverageRating(averageRating(page.getRatingDistribution(), total));
        return page;
    }

    @Override
    @Transactional
    public Result createComment(Long shopId, Long userId, ShopCommentCreateRequest request) {
        ShopCommentValidator.validate(request);
        if (shopService.getById(shopId) == null) return Result.fail("店铺不存在");
        if (count(new LambdaQueryWrapper<ShopComment>().eq(ShopComment::getShopId, shopId).eq(ShopComment::getUserId, userId)) > 0) {
            return Result.fail("你已经评价过这家店了");
        }
        ShopComment comment = new ShopComment();
        comment.setShopId(shopId);
        comment.setUserId(userId);
        comment.setRating(request.getRating());
        comment.setContent(request.getContent().trim());
        comment.setIsMock(0);
        comment.setStatus(0);
        try {
            save(comment);
        } catch (DuplicateKeyException e) {
            return Result.fail("你已经评价过这家店了");
        }
        refreshShopRating(shopId);
        return Result.ok(comment.getId());
    }

    @Override
    public java.util.List<com.hmdp.dto.ShopCommentDTO> getRecentComments(Long shopId, Integer rating, int limit) {
        Integer safeRating = rating != null && rating >= 1 && rating <= 5 ? rating : null;
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        return getBaseMapper().selectPageWithUser(shopId, safeRating, 0, safeLimit);
    }

    private LambdaQueryWrapper<ShopComment> visibleComments(Long shopId) {
        return new LambdaQueryWrapper<ShopComment>()
                .eq(ShopComment::getShopId, shopId)
                .eq(ShopComment::getStatus, 0);
    }

    private Map<Integer, Long> ratingDistribution(Long shopId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 5; rating >= 1; rating--) {
            distribution.put(rating, (long) count(visibleComments(shopId).eq(ShopComment::getRating, rating)));
        }
        return distribution;
    }

    private Double averageRating(Map<Integer, Long> distribution, long total) {
        if (total == 0) return 0D;
        long weighted = 0;
        for (Map.Entry<Integer, Long> entry : distribution.entrySet()) weighted += entry.getKey() * entry.getValue();
        return BigDecimal.valueOf((double) weighted / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private void refreshShopRating(Long shopId) {
        Map<Integer, Long> distribution = ratingDistribution(shopId);
        long total = 0;
        for (Long value : distribution.values()) total += value;
        int score = total == 0 ? 0 : (int) Math.round(averageRating(distribution, total) * 10);
        Shop shop = new Shop();
        shop.setId(shopId);
        shop.setComments((int) total);
        shop.setScore(score);
        shopService.update(shop);
    }
}
