package com.volunteer.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * NL2SQL 请求：自然语言转 SQL 查询
 */
@Data
public class NL2SqlDTO {

    /** 用户输入的自然语言问题，如 "查询本月所有已结束的活动" */
    @NotBlank(message = "查询内容不能为空")
    private String query;
}
