package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.activity.entity.VolReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VolReportMapper extends BaseMapper<VolReport> {

    @Select("SELECT COUNT(*) FROM vol_report WHERE activity_id=#{activityId} AND status IN (0, 1)")
    int countPendingByActivityId(@Param("activityId") Long activityId);

    @Select("SELECT COUNT(*) FROM vol_report WHERE reported_user_id=#{userId} AND status IN (0, 1)")
    int countPendingByReportedUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM vol_report WHERE activity_id=#{activityId} AND reporter_id=#{reporterId} AND status IN (0, 1)")
    int countByActivityAndReporter(@Param("activityId") Long activityId, @Param("reporterId") Long reporterId);
}
