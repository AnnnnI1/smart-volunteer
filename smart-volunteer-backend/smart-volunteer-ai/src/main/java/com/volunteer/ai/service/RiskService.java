package com.volunteer.ai.service;

import com.volunteer.common.entity.ResponseResult;

public interface RiskService {
    /** 分析全部志愿者，返回流失风险预警列表 */
    ResponseResult predictChurnRisk();
}
