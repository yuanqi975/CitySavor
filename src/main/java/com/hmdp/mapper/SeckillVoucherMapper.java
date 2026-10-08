package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.dto.SeckillVoucherDTO;
import com.hmdp.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {
    List<SeckillVoucherDTO> selectSeckillVouchers(@Param("status") String status);
}
