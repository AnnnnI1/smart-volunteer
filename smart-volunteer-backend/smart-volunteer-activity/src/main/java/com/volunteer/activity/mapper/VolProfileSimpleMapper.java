package com.volunteer.activity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 轻量 Mapper：仅在活动服务内更新 vol_profile.total_hours
 * 避免跨服务 Feign 调用，同库直接 UPDATE
 */
@Mapper
public interface VolProfileSimpleMapper {

    @Update("<script>" +
            "UPDATE vol_profile SET total_hours = total_hours + #{hours} " +
            "WHERE user_id IN " +
            "<foreach item='id' collection='userIds' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    void batchAddHours(@Param("userIds") List<Long> userIds, @Param("hours") int hours);
}
