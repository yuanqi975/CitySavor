package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ISeckillQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/seckill")
public class SeckillController {
    @Resource
    private ISeckillQueryService seckillQueryService;

    @GetMapping("/list")
    public Result list(@RequestParam(value = "status", defaultValue = "all") String status) {
        return seckillQueryService.list(status);
    }
}
