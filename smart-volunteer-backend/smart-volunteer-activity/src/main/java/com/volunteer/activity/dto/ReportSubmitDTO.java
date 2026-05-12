package com.volunteer.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportSubmitDTO {

    @NotBlank(message = "举报类型不能为空")
    private String reportType;

    @NotBlank(message = "分类编号不能为空")
    private String categoryCode;

    private Long activityId;

    private Long reportedUserId;

    @NotBlank(message = "举报说明不能为空")
    private String description;

    private java.util.List<String> evidenceUrls;

    private Integer priority;
}
