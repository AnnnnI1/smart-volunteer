package com.volunteer.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import com.volunteer.common.utils.BeanCopyUtils;
import com.volunteer.common.utils.JwtUtil;
import com.volunteer.common.utils.Md5Util;
import com.volunteer.common.vo.PageVo;
import com.volunteer.user.dto.UserChangePwdDTO;
import com.volunteer.user.dto.UserLoginDTO;
import com.volunteer.user.dto.UserUpdateDTO;
import com.volunteer.user.entity.User;
import com.volunteer.user.entity.VolProfile;
import com.volunteer.user.mapper.UserMapper;
import com.volunteer.user.mapper.VolProfileMapper;
import com.volunteer.user.service.UserService;
import com.volunteer.user.utils.RedisCache;
import com.volunteer.user.vo.UserAdminVo;
import com.volunteer.user.vo.UserInfoVo;
import com.volunteer.user.vo.UserLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RedisCache redisCache;
    @Autowired
    private VolProfileMapper profileMapper;

    @Value("${python.ai.url:http://localhost:9094}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public ResponseResult register(String username, String nickname, String password, Integer role) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        if (!Objects.isNull(userMapper.selectOne(queryWrapper))) {
            return ResponseResult.errorResult(AppHttpCodeEnum.USERNAME_EXIST);
        }
        if (role == null) role = 1;
        if (role != 1 && role != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "角色值非法");
        }
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(Md5Util.getMD5String(password));
        user.setRole(role);
        userMapper.insert(user);
        return ResponseResult.okResult();
    }

    @Override
    @Transactional
    public ResponseResult login(UserLoginDTO request) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, request.getUsername())
                    .eq(User::getStatus, 1);
        User user = userMapper.selectOne(queryWrapper);

        if (Objects.isNull(user) || !Md5Util.getMD5String(request.getPassword()).equals(user.getPasswordHash())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_ERROR);
        }

        String userId = user.getId().toString();
        String jwt = JwtUtil.createJWT(userId, Map.of("role", user.getRole()));
        UserInfoVo userInfoVo = BeanCopyUtils.copyBean(user, UserInfoVo.class);
        redisCache.setCacheObject("login:" + userId, userInfoVo);
        return ResponseResult.okResult(new UserLoginVo(jwt, userInfoVo));
    }

    @Override
    @Transactional
    public ResponseResult updateInfo(UserUpdateDTO request) {
        // 空字符串转 null，避免 email/phone UNIQUE 约束冲突
        if (request.getEmail() != null && request.getEmail().isBlank()) {
            request.setEmail(null);
        }
        if (request.getPhone() != null && request.getPhone().isBlank()) {
            request.setPhone(null);
        }
        User user = BeanCopyUtils.copyBean(request, User.class);
        userMapper.updateById(user);
        return ResponseResult.okResult();
    }

    @Override
    @Transactional
    public ResponseResult changePassword(UserChangePwdDTO request) {
        User user = userMapper.selectById(request.getId());
        if (Objects.isNull(user) || !Md5Util.getMD5String(request.getOldPassword()).equals(user.getPasswordHash())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.OLD_PASSWORD_ERROR);
        }
        user.setPasswordHash(Md5Util.getMD5String(request.getNewPassword()));
        userMapper.updateById(user);
        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult listUsers(Integer role, Boolean applyOnly, Integer page, Integer size) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        if (Boolean.TRUE.equals(applyOnly)) {
            wrapper.eq(User::getApplyOrganizer, 1);
        }
        wrapper.orderByDesc(User::getApplyOrganizer).orderByAsc(User::getRole).orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        java.util.List<UserAdminVo> voList = result.getRecords().stream()
                .map(u -> {
                    UserAdminVo vo = BeanCopyUtils.copyBean(u, UserAdminVo.class);
                    Long uid = u.getId();
                    // 补充统计字段
                    Integer credit = userMapper.selectCreditBalance(uid);
                    vo.setCreditBalance(credit != null ? credit : 0);
                    Integer signup = userMapper.countSignupByUserId(uid);
                    vo.setSignupCount(signup != null ? signup : 0);
                    Integer cancel = userMapper.countCancelByUserId(uid);
                    vo.setCancelCount(cancel != null ? cancel : 0);
                    VolProfile profile = profileMapper.selectById(uid);
                    vo.setTotalHours(profile != null && profile.getTotalHours() != null ? profile.getTotalHours() : 0);
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    @Override
    @Transactional
    public ResponseResult updateUserRole(Long targetId, Integer newRole, String operatorId, String operatorRole) {
        if (Integer.parseInt(operatorRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        if (newRole == null || (newRole != 1 && newRole != 2)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "角色只能设为1(志愿者)或2(组织者)");
        }
        if (targetId.equals(Long.parseLong(operatorId))) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "不能修改自身角色");
        }
        User user = userMapper.selectById(targetId);
        if (Objects.isNull(user)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR, "用户不存在");
        }
        user.setRole(newRole);
        user.setApplyOrganizer(0);  // 升级/降级时清除申请标记
        userMapper.updateById(user);
        return ResponseResult.okResult();
    }

    @Override
    @Transactional
    public ResponseResult applyOrganizer(String userId, String applyReason) {
        User user = userMapper.selectById(Long.parseLong(userId));
        if (Objects.isNull(user)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR, "用户不存在");
        }
        if (user.getRole() != 1) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "仅志愿者可提交申请");
        }
        // 检查是否有待审或已通过的申请
        Integer auditStatus = user.getAuditStatus();
        if (Integer.valueOf(0).equals(auditStatus)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DUPLICATE_RECORD, "申请审核中，请等待管理员处理");
        }
        if (Integer.valueOf(1).equals(auditStatus)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DUPLICATE_RECORD, "您已是组织者，无需重复申请");
        }

        // 查询志愿者历史数据
        VolProfile profile = profileMapper.selectById(Long.parseLong(userId));
        int totalHours = profile != null ? (profile.getTotalHours() != null ? profile.getTotalHours() : 0) : 0;
        String skills = profile != null ? profile.getSkills() : "";

        // 查询报名统计
        Integer signupCount = userMapper.countSignupByUserId(Long.parseLong(userId));
        Integer cancelCount = userMapper.countCancelByUserId(Long.parseLong(userId));
        if (signupCount == null) signupCount = 0;
        if (cancelCount == null) cancelCount = 0;

        // 查询积分
        Integer creditBalance = userMapper.selectCreditBalance(Long.parseLong(userId));
        if (creditBalance == null) creditBalance = 0;

        // 调用 Python AI 服务进行 AI 尽调
        String aiAuditReport = callAiOrganizerAudit(
                Long.parseLong(userId),
                user.getUsername(),
                user.getNickname(),
                applyReason,
                totalHours,
                signupCount,
                cancelCount,
                skills,
                creditBalance
        );

        // 保存申请记录
        user.setApplyOrganizer(1);
        user.setApplyReason(applyReason);
        user.setAuditStatus(0);  // 默认待审
        user.setAiAuditReport(aiAuditReport);
        userMapper.updateById(user);

        log.info("组织者申请已提交 userId={} applyReason={}", userId, applyReason);
        return ResponseResult.okResult("您的申请已提交，正在进行AI资质评估与人工复核，请等待管理员��核");
    }

    /**
     * 调用 Python AI 服务进行组织者入驻尽调
     */
    private String callAiOrganizerAudit(
            Long userId,
            String username,
            String nickname,
            String applyReason,
            int totalHours,
            int signupCount,
            int cancelCount,
            String skills,
            int creditBalance
    ) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "user_id", userId,
                    "username", username,
                    "nickname", nickname != null ? nickname : username,
                    "apply_reason", applyReason,
                    "total_hours", totalHours,
                    "total_activities", signupCount,
                    "signup_count", signupCount,
                    "cancel_count", cancelCount,
                    "skills", skills,
                    "credit_balance", creditBalance
            );

            String responseStr = WebClient.create(pythonAiUrl)
                    .post()
                    .uri("/ai/audit/organizer")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .onErrorReturn("{\"code\":500,\"msg\":\"AI服务不可用\"}")
                    .block();

            if (responseStr != null && responseStr.contains("\"code\":200")) {
                // 提取 data 字段中的报告（JSON序列化后返回）
                log.info("AI尽调报告生成成功 userId={}", userId);
                return responseStr;
            }
            log.warn("AI尽调返回异常 userId={} response={}", userId, responseStr);
            return "{\"error\":\"AI尽调服务暂时不可用，请人工审核\"}";
        } catch (Exception e) {
            log.warn("AI尽调调用失败 userId={} error={}", userId, e.getMessage());
            return "{\"error\":\"AI尽调服务暂时不可用，请人工审核\"}";
        }
    }

    @Override
    public ResponseResult getUserMe(String userId) {
        User user = userMapper.selectById(Long.parseLong(userId));
        if (Objects.isNull(user)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR, "用户不存在");
        }
        UserInfoVo vo = BeanCopyUtils.copyBean(user, UserInfoVo.class);
        return ResponseResult.okResult(vo);
    }

    @Override
    @Transactional
    public ResponseResult auditOrganizer(Long targetId, Integer auditStatus, String operatorId, String operatorRole, String rejectReason) {
        if (Integer.parseInt(operatorRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "审核状态只能为1(通过)或2(驳回)");
        }
        User user = userMapper.selectById(targetId);
        if (Objects.isNull(user)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR, "用户不存在");
        }
        if (!Integer.valueOf(0).equals(user.getAuditStatus())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DUPLICATE_RECORD, "该申请已审核，请勿重复操作");
        }

        user.setAuditStatus(auditStatus);
        if (auditStatus == 1) {
            // 通过：将角色升级为组织者
            user.setRole(2);
            user.setApplyOrganizer(0);
            log.info("组织者申请审核通过 targetId={}", targetId);
        } else {
            // 驳回：保持志愿者角色
            user.setApplyOrganizer(0);
            log.info("组织者申请被驳回 targetId={} reason={}", targetId, rejectReason);
        }
        userMapper.updateById(user);
        return ResponseResult.okResult("审核完成");
    }

    @Override
    public Integer countSignupByUserId(Long userId) {
        return userMapper.countSignupByUserId(userId);
    }

    @Override
    public Integer countCancelByUserId(Long userId) {
        return userMapper.countCancelByUserId(userId);
    }

    @Override
    public Integer selectCreditBalance(Long userId) {
        return userMapper.selectCreditBalance(userId);
    }
}
