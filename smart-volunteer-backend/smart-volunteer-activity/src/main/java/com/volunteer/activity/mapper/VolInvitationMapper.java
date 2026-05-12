package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.activity.entity.VolInvitation;
import com.volunteer.activity.vo.InvitationVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface VolInvitationMapper extends BaseMapper<VolInvitation> {

    @Select("SELECT vi.id AS invitation_id, vi.activity_id, vi.is_read, vi.create_time AS invite_time, " +
            "va.title, va.description, va.required_skills, va.total_quota, va.joined_quota, " +
            "va.status, va.start_time, va.end_time " +
            "FROM vol_invitation vi " +
            "JOIN vol_activity va ON vi.activity_id = va.id " +
            "WHERE vi.user_id = #{userId} " +
            "ORDER BY vi.create_time DESC")
    List<InvitationVO> selectWithActivity(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM vol_invitation WHERE user_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    @Update("UPDATE vol_invitation SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    void markAllRead(@Param("userId") Long userId);
}
