package com.volunteer.activity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.volunteer.activity.dto.ReportProcessDTO;
import com.volunteer.activity.dto.ReportSubmitDTO;
import com.volunteer.activity.entity.VolReport;
import com.volunteer.common.entity.ResponseResult;

import java.util.Map;

public interface ReportService extends IService<VolReport> {

    ResponseResult submitReport(ReportSubmitDTO dto, String userId, String userRole);

    ResponseResult listReports(Integer status, Integer reportType, Integer page, Integer size);

    ResponseResult listReports(Integer status, Integer reportType, Integer page, Integer size, String reporterId, String reportedUserId);

    ResponseResult getReportDetail(Long reportId, String userId, String userRole);

    ResponseResult acceptReport(Long reportId, String userId, String userRole);

    ResponseResult processReport(Long reportId, ReportProcessDTO dto, String userId, String userRole);

    ResponseResult batchAnalyzeReports(java.util.List<Long> reportIds, String userId, String userRole);

    ResponseResult getReportStatistics(String userId, String userRole);

    ResponseResult listPenaltyRecords(Long userId, Integer page, Integer size);

    ResponseResult submitAppeal(Long reportId, String appealReason, String userId, String userRole);

    ResponseResult listAppeals(Integer decision, Integer page, Integer size, String userId, String userRole);

    ResponseResult listAppeals(Integer decision, Integer page, Integer size, String currentUserId);

    ResponseResult processAppeal(Long appealId, Integer decision, String adminResponse, String userId, String userRole);

    ResponseResult listCreditScores(Integer riskLevel, Integer page, Integer size, String userId, String userRole);

    ResponseResult getUserCreditScore(Long targetUserId, String userId, String userRole);

    ResponseResult getUserCreditScore(Long targetUserId, String currentUserId);

    ResponseResult forceUnban(Long targetUserId, String userRole);

    ResponseResult listBlacklist(Integer page, Integer size, String userId, String userRole);

    /** 管理员撤销单条惩罚记录 */
    ResponseResult reversePenalty(Long penaltyId, String userId, String userRole);
}
