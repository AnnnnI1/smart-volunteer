package com.volunteer.activity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReportProcessDTO {

    @NotNull(message = "处理决定不能为空")
    private Integer decision;

    private String adminDecision;

    private List<String> penaltyTypes;

    private Integer creditDeduct;

    private Integer banDays;

    private Boolean activityLimit;

    private Boolean demotion;
}
