package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopCommentCreateRequest;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopCommentService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;
    @Resource
    private IShopCommentService shopCommentService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
       return shopService.queryShopByType(typeId, current, x, y);
    }

    @GetMapping("/recommendations")
    public Result queryRecommendations(
            @RequestParam(value = "city", defaultValue = "xian") String city,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize
    ) {
        if (!"xian".equalsIgnoreCase(city)) return Result.ok(java.util.Collections.emptyList());
        int safeSize = Math.min(Math.max(pageSize, 1), 12);
        Page<Shop> page = shopService.query().likeRight("address", "西安市")
                .orderByDesc("id")
                .page(new Page<>(Math.max(current, 1), safeSize));
        return Result.ok(page.getRecords());
    }

    @GetMapping("/{shopId}/comments")
    public Result queryShopComments(
            @PathVariable Long shopId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
    ) {
        return shopCommentService.queryComments(shopId, current, pageSize);
    }

    @PostMapping("/{shopId}/comments")
    public Result createShopComment(@PathVariable Long shopId, @RequestBody ShopCommentCreateRequest request) {
        if (UserHolder.getUser() == null) return Result.fail("请先登录");
        return shopCommentService.createComment(shopId, UserHolder.getUser().getId(), request);
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
