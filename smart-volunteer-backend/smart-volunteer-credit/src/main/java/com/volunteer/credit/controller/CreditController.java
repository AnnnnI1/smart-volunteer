package com.volunteer.credit.controller;

import com.volunteer.common.entity.ResponseResult;
import com.volunteer.credit.service.CreditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/credit")
public class CreditController {

    @Autowired
    private CreditService creditService;

    /** 查询当前登录用户积分余额 */
    @GetMapping("/balance")
    public ResponseResult getBalance(@RequestHeader("X-User-Id") String userId) {
        return creditService.getBalance(Long.parseLong(userId));
    }

    /** 查询当前登录用户积分流水（分页） */
    @GetMapping("/records")
    public ResponseResult getRecords(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1")  Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return creditService.getRecords(Long.parseLong(userId), page, size);
    }

    /** 管理员手动调整积分 */
    @PostMapping("/admin/adjust")
    public ResponseResult adminAdjust(@RequestBody Map<String, Object> body) {
        Long   userId = toLong(body.get("userId"));
        int    delta  = ((Number) body.get("delta")).intValue();
        String remark = (String) body.getOrDefault("remark", "管理员调整");
        return creditService.adminAdjust(userId, delta, remark);
    }

    /** 内部接口：活动服务通过 MQ 消息触发（此接口保留供测试/降级用） */
    @PostMapping("/internal/award-all/{activityId}")
    public ResponseResult awardAll(@PathVariable Long activityId) {
        creditService.awardAllForActivity(activityId);
        return ResponseResult.okResult();
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }
}
