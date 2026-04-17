package com.volunteer.user.controller;

import com.volunteer.common.entity.ResponseResult;
import com.volunteer.user.dto.ProfileUpdateDTO;
import com.volunteer.user.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/profile")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    /** 查询我的档案 */
    @GetMapping
    public ResponseResult getMyProfile(@RequestHeader("X-User-Id") String userId) {
        return profileService.getMyProfile(Long.parseLong(userId));
    }

    /** 新增或更新我的档案（Upsert） */
    @PutMapping
    public ResponseResult saveOrUpdate(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody ProfileUpdateDTO dto) {
        return profileService.saveOrUpdateProfile(Long.parseLong(userId), dto);
    }

    /** 查询指定用户的档案（管理员 / AI 服务内调用） */
    @GetMapping("/{userId}")
    public ResponseResult getByUserId(@PathVariable Long userId) {
        return profileService.getProfileByUserId(userId);
    }
}
