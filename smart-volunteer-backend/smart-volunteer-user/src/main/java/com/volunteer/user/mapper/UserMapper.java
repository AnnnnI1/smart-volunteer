package com.volunteer.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT COUNT(*) FROM vol_registration WHERE user_id=#{userId}")
    Integer countSignupByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM vol_registration WHERE user_id=#{userId} AND status=1")
    Integer countCancelByUserId(@Param("userId") Long userId);

    @Select("SELECT balance FROM vol_credit_balance WHERE user_id=#{userId}")
    Integer selectCreditBalance(@Param("userId") Long userId);

    @Select("SELECT credit_score FROM vol_user_credit_score WHERE user_id=#{userId}")
    Integer selectCreditScore(@Param("userId") Long userId);

    @Select("SELECT nickname FROM users WHERE id=#{userId}")
    String selectNicknameById(@Param("userId") Long userId);

    @Select("SELECT username FROM users WHERE id=#{userId}")
    String selectUsernameById(@Param("userId") Long userId);
}
