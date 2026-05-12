package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.activity.entity.VolRegistration;
import com.volunteer.activity.vo.RegistrationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface VolRegistrationMapper extends BaseMapper<VolRegistration> {

    /**
     * 将已取消的报名记录重新激活（原子操作，WHERE status=1 保证并发安全）
     * @return 影响行数，0 表示没有可重新激活的记录
     */
    @Update("UPDATE vol_registration SET status=0 WHERE user_id=#{userId} AND activity_id=#{activityId} AND status=1")
    int reactivateCancelled(@Param("userId") Long userId, @Param("activityId") Long activityId);

    /**
     * 活动结束时，将 status=0（已报名未签到）的记录标记为缺席（status=4）
     */
    @Update("UPDATE vol_registration SET status=4 WHERE activity_id=#{id} AND status=0")
    void markAbsent(@Param("id") Long activityId);

    @Select("SELECT vr.id, vr.user_id as userId, vr.activity_id as activityId, " +
            "vr.status, vr.create_time as createTime, vr.checkin_code as checkinCode, " +
            "u.username, u.nickname, u.phone " +
            "FROM vol_registration vr " +
            "LEFT JOIN users u ON vr.user_id = u.id " +
            "WHERE vr.activity_id = #{activityId} AND vr.status != 1 " +
            "ORDER BY vr.status ASC, vr.create_time ASC")
    List<RegistrationVO> selectWithUserInfo(@Param("activityId") Long activityId);

    /**
     * 查询活动中所有已签到（status=2）的用户 ID，用于活动结束时累加服务时长
     */
    @Select("SELECT user_id FROM vol_registration WHERE activity_id=#{activityId} AND status=2")
    List<Long> selectCheckedInUserIds(@Param("activityId") Long activityId);

    /**
     * 通过个人专属签到码精确查询报名记录（防作弊关键）
     */
    @Select("SELECT * FROM vol_registration WHERE checkin_code=#{checkinCode} AND status=0 LIMIT 1")
    VolRegistration selectByCheckinCode(@Param("checkinCode") String checkinCode);

    /**
     * 通过个人专属签到码查询（不限制状态，用于展示）
     */
    @Select("SELECT * FROM vol_registration WHERE checkin_code=#{checkinCode} LIMIT 1")
    VolRegistration selectByCheckinCodeAnyStatus(@Param("checkinCode") String checkinCode);
}
