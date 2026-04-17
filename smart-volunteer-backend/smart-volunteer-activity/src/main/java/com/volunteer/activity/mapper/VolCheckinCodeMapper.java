package com.volunteer.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.activity.entity.VolCheckinCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VolCheckinCodeMapper extends BaseMapper<VolCheckinCode> {

    @Select("SELECT * FROM vol_checkin_code WHERE activity_id=#{id} AND is_active=1 AND expire_at > NOW() LIMIT 1")
    VolCheckinCode selectActive(@Param("id") Long activityId);

    @Update("UPDATE vol_checkin_code SET is_active=0 WHERE activity_id=#{id}")
    void deactivateAll(@Param("id") Long activityId);
}
