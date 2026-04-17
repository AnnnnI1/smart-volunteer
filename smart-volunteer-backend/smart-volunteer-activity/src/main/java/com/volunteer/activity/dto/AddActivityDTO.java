package com.volunteer.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AddActivityDTO {

    @NotBlank(message = "活动标题不能为空")
    private String title;

    private String description;

    /** 活动所需技能（逗号分隔），供 KNN 推荐使用 */
    private String requiredSkills;

    @NotNull(message = "总名额不能为空")
    @Min(value = 1, message = "总名额至少为1")
    private Integer totalQuota;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
}
