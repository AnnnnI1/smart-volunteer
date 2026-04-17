package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.activity.entity.VolActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VolActivityMapper extends BaseMapper<VolActivity> {

    /** 原子递增已报名额（防并发下 joined_quota 不准） */
    @Update("UPDATE vol_activity SET joined_quota = joined_quota + 1 WHERE id = #{id}")
    int incrementJoinedQuota(Long id);

    /** 原子递减已报名额（取消报名时使用） */
    @Update("UPDATE vol_activity SET joined_quota = joined_quota - 1 WHERE id = #{id} AND joined_quota > 0")
    int decrementJoinedQuota(Long id);

    @Select("SELECT nickname FROM users WHERE id = #{id}")
    String selectNicknameById(@Param("id") Long id);
}
