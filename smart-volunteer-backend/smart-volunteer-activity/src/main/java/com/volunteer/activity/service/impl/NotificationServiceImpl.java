package com.volunteer.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.volunteer.activity.entity.VolNotification;
import com.volunteer.activity.mapper.VolNotificationMapper;
import com.volunteer.activity.service.NotificationService;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.vo.PageVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<VolNotificationMapper, VolNotification>
        implements NotificationService {

    @Autowired private VolNotificationMapper notificationMapper;

    @Override
    public void send(Long userId, String title, String content, String type, Long relatedId) {
        if (userId == null) return;
        VolNotification notification = new VolNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
        log.info("[通知发送] userId={} type={} relatedId={} title={}", userId, type, relatedId, title);
    }

    @Override
    public ResponseResult getUnreadCount(Long userId) {
        long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<VolNotification>()
                        .eq(VolNotification::getUserId, userId)
                        .eq(VolNotification::getIsRead, 0)
        );
        return ResponseResult.okResult(Map.of("unreadCount", count));
    }

    @Override
    public ResponseResult listNotifications(Long userId, Integer page, Integer size) {
        Page<VolNotification> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<VolNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VolNotification::getUserId, userId);
        wrapper.orderByDesc(VolNotification::getCreatedAt);
        Page<VolNotification> result = notificationMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> voList = result.getRecords().stream()
                .map(n -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("content", n.getContent());
                    m.put("type", n.getType());
                    m.put("typeDesc", typeDesc(n.getType()));
                    m.put("relatedId", n.getRelatedId());
                    m.put("isRead", n.getIsRead());
                    m.put("createdAt", n.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseResult.okResult(new PageVo(voList, result.getTotal()));
    }

    @Override
    public ResponseResult markAsRead(Long notificationId, Long userId) {
        VolNotification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            return ResponseResult.errorResult(1, "通知不存在");
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
        return ResponseResult.okResult(null);
    }

    @Override
    public ResponseResult markAllAsRead(Long userId) {
        VolNotification update = new VolNotification();
        update.setIsRead(1);
        notificationMapper.update(update,
                new LambdaQueryWrapper<VolNotification>()
                        .eq(VolNotification::getUserId, userId)
                        .eq(VolNotification::getIsRead, 0)
        );
        return ResponseResult.okResult(null);
    }

    private String typeDesc(String type) {
        return switch (type) {
            case "REPORT_SUBMIT" -> "举报通知";
            case "REPORT_ACCEPTED" -> "受理通知";
            case "REPORT_RESOLVED" -> "处理结果通知";
            case "APPEAL_SUBMITTED" -> "新申诉通知";
            case "APPEAL_RESULT" -> "申诉结果通知";
            default -> "系统通知";
        };
    }
}
