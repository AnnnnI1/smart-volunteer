package com.volunteer.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.credit.entity.VolCreditBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VolCreditBalanceMapper extends BaseMapper<VolCreditBalance> {

    /** 原子性地将积分变化写入余额（不存在则初始化，存在则累加） */
    @Update("INSERT INTO vol_credit_balance(user_id, balance) VALUES(#{userId}, #{delta}) " +
            "ON DUPLICATE KEY UPDATE balance = balance + #{delta}")
    void upsertBalance(@Param("userId") Long userId, @Param("delta") int delta);

    @Select("SELECT COALESCE(balance, 0) FROM vol_credit_balance WHERE user_id = #{userId}")
    Integer selectBalance(@Param("userId") Long userId);
}
