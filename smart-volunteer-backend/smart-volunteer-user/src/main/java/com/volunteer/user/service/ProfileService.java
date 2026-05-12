package com.volunteer.user.service;

import com.volunteer.common.entity.ResponseResult;
import com.volunteer.user.dto.ProfileUpdateDTO;

public interface ProfileService {

    /** 查询我的档案 */
    ResponseResult getMyProfile(Long userId);

    /** 新增或更新档案（Upsert） */
    ResponseResult saveOrUpdateProfile(Long userId, ProfileUpdateDTO dto);

    /** 查询指定用户的档案（供 AI 服务调用或管理员查看） */
    ResponseResult getProfileByUserId(Long userId);
}
