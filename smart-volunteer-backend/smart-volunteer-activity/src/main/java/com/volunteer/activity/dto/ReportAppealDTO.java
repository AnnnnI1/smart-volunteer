package com.volunteer.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportAppealDTO {

    @NotBlank(message = "申诉理由不能为空")
    private String appealReason;
}
