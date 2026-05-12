package com.volunteer.activity.controller;

import com.volunteer.activity.dto.ReportAppealDTO;
import com.volunteer.activity.dto.ReportProcessDTO;
import com.volunteer.activity.dto.ReportSubmitDTO;
import com.volunteer.activity.service.ReportService;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /** 用户提交举报（志愿者/组织者/管理员均可） */
    @PostMapping("/submit")
    public ResponseResult submitReport(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestBody @Valid ReportSubmitDTO dto) {
        return reportService.submitReport(dto, userId, userRole);
    }

    /** 用户查询自己提交的举报列表 */
    @GetMapping("/my/reports")
    public ResponseResult listMyReports(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return reportService.listReports(null, null, page, size, userId, null);
    }

    /** 用户查询举报自己的列表 */
    @GetMapping("/my/received")
    public ResponseResult listMyReceivedReports(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return reportService.listReports(null, null, page, size, null, userId);
    }

    /** 用户查看自己的信用分 */
    @GetMapping("/credit/{userId}")
    public ResponseResult getMyCreditScore(
            @RequestHeader("X-User-Id") String currentUserId,
            @PathVariable Long userId) {
        return reportService.getUserCreditScore(userId, currentUserId);
    }

    /** 用户查询自己的申诉列表 */
    @GetMapping("/my/appeals")
    public ResponseResult listMyAppeals(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return reportService.listAppeals(null, page, size, userId);
    }

    /** 管理员查询举报列表 */
    @GetMapping("/admin/list")
    public ResponseResult listReports(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer reportType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.listReports(status, reportType, page, size);
    }

    /** 管理员获取举报详情 */
    @GetMapping("/admin/{reportId}")
    public ResponseResult getReportDetail(
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long reportId) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.getReportDetail(reportId, null, userRole);
    }

    /** 管理员受理举报（领取） */
    @PostMapping("/admin/{reportId}/accept")
    public ResponseResult acceptReport(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long reportId) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.acceptReport(reportId, userId, userRole);
    }

    /** 管理员处理举报（采纳AI建议/调整惩罚/否决） */
    @PostMapping("/admin/{reportId}/process")
    public ResponseResult processReport(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long reportId,
            @RequestBody ReportProcessDTO dto) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.processReport(reportId, dto, userId, userRole);
    }

    /** 管理员批量 AI 分析举报 */
    @PostMapping("/admin/batch-analyze")
    public ResponseResult batchAnalyze(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestBody Map<String, List<Long>> body) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        List<Long> reportIds = body.get("reportIds");
        if (reportIds == null || reportIds.isEmpty()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "reportIds 不能为空");
        }
        return reportService.batchAnalyzeReports(reportIds, userId, userRole);
    }

    /** 管理员获取举报统计数据 */
    @GetMapping("/admin/statistics")
    public ResponseResult getStatistics(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.getReportStatistics(userId, userRole);
    }

    /** 管理员查看惩罚记录 */
    @GetMapping("/admin/penalties")
    public ResponseResult listPenaltyRecords(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.listPenaltyRecords(null, page, size);
    }

    /** 被举报人提交申诉 */
    @PostMapping("/appeal/{reportId}")
    public ResponseResult submitAppeal(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long reportId,
            @RequestBody @Valid ReportAppealDTO dto) {
        return reportService.submitAppeal(reportId, dto.getAppealReason(), userId, userRole);
    }

    /** 管理员查看申诉列表 */
    @GetMapping("/admin/appeals")
    public ResponseResult listAppeals(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(required = false) Integer decision,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.listAppeals(decision, page, size, null, userRole);
    }

    /** 管理员处理申诉 */
    @PostMapping("/admin/appeals/{appealId}/process")
    public ResponseResult processAppeal(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long appealId,
            @RequestBody Map<String, Object> body) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        Integer decision = (Integer) body.get("decision");
        String adminResponse = (String) body.get("adminResponse");
        return reportService.processAppeal(appealId, decision, adminResponse, userId, userRole);
    }

    /** 管理员查看信用分列表 */
    @GetMapping("/admin/credits")
    public ResponseResult listCreditScores(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.listCreditScores(riskLevel, page, size, null, userRole);
    }

    /** 管理员查看用户信用分详情 */
    @GetMapping("/admin/credit/{userId}")
    public ResponseResult getUserCreditScore(
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long userId) {
        return reportService.getUserCreditScore(userId, null, userRole);
    }

    /** 管理员强制解封用户 */
    @PostMapping("/admin/unban/{targetUserId}")
    public ResponseResult forceUnban(
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long targetUserId) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.forceUnban(targetUserId, userRole);
    }

    /** 管理员查看黑名单 */
    @GetMapping("/admin/blacklist")
    public ResponseResult listBlacklist(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.listBlacklist(page, size, null, userRole);
    }

    /** 管理员撤销单条惩罚记录 */
    @PostMapping("/admin/penalty/{penaltyId}/reverse")
    public ResponseResult reversePenalty(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable Long penaltyId) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return reportService.reversePenalty(penaltyId, userId, userRole);
    }
}
