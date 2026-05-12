package com.volunteer.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.credit.entity.VolCreditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VolCreditRecordMapper extends BaseMapper<VolCreditRecord> {

    /** 幂等检查：该用户该活动该类型是否已处理过 */
    @Select("SELECT COUNT(1) FROM vol_credit_record " +
            "WHERE user_id = #{userId} AND activity_id = #{activityId} AND change_type = #{type}")
    int existsRecord(@Param("userId") Long userId,
                     @Param("activityId") Long activityId,
                     @Param("type") int type);
}
