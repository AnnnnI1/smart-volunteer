package com.volunteer.ai.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * NL2SQL 查询结果
 */
@Data
public class NL2SqlVO {

    /** AI 生成的 SQL（脱敏后返回，方便前端展示） */
    private String sql;

    /** 查询结果数据 */
    private List<Map<String, Object>> data;

    /** 命中行数 */
    private int total;
}
