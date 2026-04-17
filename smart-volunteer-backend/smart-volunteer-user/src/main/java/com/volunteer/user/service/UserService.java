package com.volunteer.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.user.dto.UserChangePwdDTO;
import com.volunteer.user.dto.UserLoginDTO;
import com.volunteer.user.dto.UserUpdateDTO;
import com.volunteer.user.entity.User;

public interface UserService extends IService<User> {

    ResponseResult register(String username, String nickname, String password, Integer role);

    ResponseResult login(UserLoginDTO request);

    ResponseResult updateInfo(UserUpdateDTO request);

    ResponseResult changePassword(UserChangePwdDTO request);

    /** 管理员查询用户列表（可按 role 过滤，applyOnly=true 只看申请者） */
    ResponseResult listUsers(Integer role, Boolean applyOnly, Integer page, Integer size);

    /** 管理员修改用户角色 */
    ResponseResult updateUserRole(Long targetId, Integer newRole, String operatorId, String operatorRole);

    /** 志愿者申请成为组织者（含AI尽调） */
    ResponseResult applyOrganizer(String userId, String applyReason);

    /** 管理员审核组织者申请 */
    ResponseResult auditOrganizer(Long targetId, Integer auditStatus, String operatorId, String operatorRole, String rejectReason);

    /** 查询用户的报名次数 */
    Integer countSignupByUserId(Long userId);

    /** 查询用户取消报名次数 */
    Integer countCancelByUserId(Long userId);

    /** 查询用户积分余额 */
    Integer selectCreditBalance(Long userId);

    /** 获取当前登录用户的最新信息（从 DB 读取，不依赖 JWT） */
    ResponseResult getUserMe(String userId);
}
