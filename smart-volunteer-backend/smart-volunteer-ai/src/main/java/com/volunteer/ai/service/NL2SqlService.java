package com.volunteer.ai.service;

import com.volunteer.common.entity.ResponseResult;

public interface NL2SqlService {

    /**
     * 将自然语言转换为 SQL 并执行查询
     *
     * @param query 自然语言问题
     */
    ResponseResult query(String query);
}
