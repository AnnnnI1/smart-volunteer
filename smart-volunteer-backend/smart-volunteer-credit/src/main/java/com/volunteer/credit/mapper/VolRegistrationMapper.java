package com.volunteer.credit.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 直接访问 vol_registration 表（同库跨服务查询，获取活动已签到者列表）
 */
@Mapper
public interface VolRegistrationMapper {

    @Select("SELECT user_id FROM vol_registration WHERE activity_id = #{activityId} AND status = 2")
    List<Long> selectUserIdsByActivity(@Param("activityId") Long activityId);

    @Select("SELECT user_id FROM vol_registration WHERE activity_id = #{activityId} AND status = 4")
    List<Long> selectAbsentUserIdsByActivity(@Param("activityId") Long activityId);
}
