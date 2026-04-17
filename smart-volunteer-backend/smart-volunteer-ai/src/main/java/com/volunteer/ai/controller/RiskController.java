package com.volunteer.ai.controller;

import com.volunteer.ai.service.RiskService;
import com.volunteer.common.entity.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/risk")
public class RiskController {

    @Autowired
    private RiskService riskService;

    /**
     * 志愿者流失风险预警
     * 聚合行为数据 → 调用 Python AI 节点评分 → 返回预警列表
     */
    @GetMapping("/predict")
    public ResponseResult predict() {
        return riskService.predictChurnRisk();
    }
}
