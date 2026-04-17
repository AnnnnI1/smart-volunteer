package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.VolActivity;
import com.volunteer.activity.entity.VolInvitation;
import com.volunteer.activity.entity.VolRegistration;
import com.volunteer.activity.mapper.VolActivityMapper;
import com.volunteer.activity.mapper.VolInvitationMapper;
import com.volunteer.activity.mapper.VolRegistrationMapper;
import com.volunteer.activity.service.InvitationService;
import com.volunteer.activity.utils.RedisCache;
import com.volunteer.activity.vo.InvitationVO;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class InvitationServiceImpl implements InvitationService {

    private static final String QUOTA_KEY = "activity:quota:";

    @Autowired private VolInvitationMapper invitationMapper;
    @Autowired private VolActivityMapper activityMapper;
    @Autowired private VolRegistrationMapper registrationMapper;
    @Autowired private RedisCache redisCache;

    @Override
    public ResponseResult sendInvitation(Long activityId, Long userId) {
        // 校验活动
        VolActivity activity = activityMapper.selectById(activityId);
        if (Objects.isNull(activity)) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        if (activity.getStatus() == 3) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "活动已结束，无法发送邀请");
        }

        // 校验是否已报名
        LambdaQueryWrapper<VolRegistration> regWrapper = new LambdaQueryWrapper<>();
        regWrapper.eq(VolRegistration::getUserId, userId)
                  .eq(VolRegistration::getActivityId, activityId)
                  .in(VolRegistration::getStatus, 0, 2);
        if (registrationMapper.selectCount(regWrapper) > 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "该志愿者已报名此活动");
        }

        // 插入邀请（唯一索引防重复）
        try {
            VolInvitation inv = new VolInvitation();
            inv.setActivityId(activityId);
            inv.setUserId(userId);
            inv.setIsRead(0);
            invitationMapper.insert(inv);
        } catch (DuplicateKeyException e) {
            return ResponseResult.errorResult(AppHttpCodeEnum.REQUEST_NOT_MATCH, "已向该志愿者发送过邀请");
        }

        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getMyInvitations(Long userId) {
        List<InvitationVO> list = invitationMapper.selectWithActivity(userId);
        // 补充 remainQuota：优先从 Redis 取，Redis 无则用 DB 计算
        for (InvitationVO vo : list) {
            String key = QUOTA_KEY + vo.getActivityId();
            String redisVal = redisCache.get(key);
            if (redisVal != null) {
                vo.setRemainQuota(Integer.parseInt(redisVal));
            } else {
                vo.setRemainQuota(Math.max(0, vo.getTotalQuota() - vo.getJoinedQuota()));
            }
        }
        int unread = (int) list.stream().filter(v -> v.getIsRead() != null && v.getIsRead() == 0).count();
        return ResponseResult.okResult(Map.of("list", list, "unread", unread));
    }

    @Override
    public ResponseResult markAllRead(Long userId) {
        invitationMapper.markAllRead(userId);
        return ResponseResult.okResult(0);
    }

    @Override
    public ResponseResult getUnreadCount(Long userId) {
        return ResponseResult.okResult(invitationMapper.countUnread(userId));
    }

    @Override
    public ResponseResult deleteInvitation(Long invitationId, Long userId) {
        LambdaQueryWrapper<VolInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolInvitation::getId, invitationId)
               .eq(VolInvitation::getUserId, userId);
        int rows = invitationMapper.delete(wrapper);
        if (rows == 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR);
        }
        return ResponseResult.okResult();
    }
}
