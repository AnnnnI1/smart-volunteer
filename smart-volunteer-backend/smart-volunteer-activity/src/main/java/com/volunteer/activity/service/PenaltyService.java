package com.volunteer.activity.service;

import com.volunteer.common.entity.ResponseResult;

import java.util.Map;

public interface PenaltyService {

    ResponseResult deductCredit(Long userId, Integer amount, Long reportId, String reason, Long operatorId);

    ResponseResult banUser(Long userId, Integer banType, Integer days, String reason, Long reportId, Long operatorId);

    ResponseResult unbanUser(Long userId, Long operatorId);

    ResponseResult limitActivityCreation(Long userId, Integer days, String reason, Long reportId, Long operatorId);

    ResponseResult demoteOrganizer(Long userId, Long reportId, Long operatorId);

    ResponseResult recordPenalty(String penaltyType, Map<String, Object> penaltyValue, Long userId,
                                 Long reportId, Long operatorId, String aiSuggested, String reason);

    void checkAndRecoverCredit();
}
