package com.volunteer.user.controller;

import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import com.volunteer.user.dto.UserChangePwdDTO;
import com.volunteer.user.dto.UserLoginDTO;
import com.volunteer.user.dto.UserRegisterDTO;
import com.volunteer.user.dto.UserUpdateDTO;
import com.volunteer.user.service.OssService;
import com.volunteer.user.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private OssService ossService;

    @PostMapping("/register")
    public ResponseResult register(@RequestBody @Valid UserRegisterDTO request) {
        return userService.register(request.getUsername(), request.getNickname(), request.getPassword(), request.getRole());
    }

    @PostMapping("/login")
    public ResponseResult login(@RequestBody @Valid UserLoginDTO request) {
        return userService.login(request);
    }

    @PostMapping("/updateInfo")
    public ResponseResult updateInfo(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid UserUpdateDTO request) {
        request.setId(Long.parseLong(userId));
        return userService.updateInfo(request);
    }

    @PostMapping("/changePassword")
    public ResponseResult changePassword(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid UserChangePwdDTO request) {
        request.setId(Long.parseLong(userId));
        return userService.changePassword(request);
    }

    /**
     * 上传头像到 OSS，返回新头像 URL 并更新数据库
     */
    @PostMapping("/avatar")
    public ResponseResult uploadAvatar(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "请选择图片文件");
        }
        try {
            Long uid = Long.parseLong(userId);
            String url = ossService.uploadAvatar(file, uid);
            // 更新数据库 avatar 字段
            UserUpdateDTO dto = new UserUpdateDTO();
            dto.setId(uid);
            dto.setAvatar(url);
            userService.updateInfo(dto);
            return ResponseResult.okResult(url);
        } catch (IllegalArgumentException e) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, e.getMessage());
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR, "上传失败，请稍后重试");
        }
    }

    /** 管理员查询用户列表（可按 role 过滤，applyOnly=true 只看申请者） */
    @GetMapping("/admin/list")
    public ResponseResult listUsers(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Boolean applyOnly,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return userService.listUsers(role, applyOnly, page, size);
    }

    /** 志愿者申请成为组织者（含AI尽调） */
    @PostMapping("/apply-organizer")
    public ResponseResult applyOrganizer(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String applyReason = body.get("applyReason");
        if (applyReason == null || applyReason.isBlank()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "请填写申请理由");
        }
        return userService.applyOrganizer(userId, applyReason);
    }

    /** 管理员审核组织者申请 */
    @PutMapping("/admin/{id}/audit-organizer")
    public ResponseResult auditOrganizer(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long id,
            @RequestParam Integer auditStatus,
            @RequestParam(required = false) String rejectReason) {
        return userService.auditOrganizer(id, auditStatus, userId, userRole, rejectReason);
    }

    /** 获取当前登录用户的最新信息（从 DB 读取，不依赖 JWT） */
    @GetMapping("/me")
    public ResponseResult getMe(@RequestHeader("X-User-Id") String userId) {
        return userService.getUserMe(userId);
    }

    /** 管理员修改用户角色（志愿者 ↔ 组织者） */
    @PutMapping("/admin/{id}/role")
    public ResponseResult updateUserRole(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long id,
            @RequestParam Integer role) {
        return userService.updateUserRole(id, role, userId, userRole);
    }
}
