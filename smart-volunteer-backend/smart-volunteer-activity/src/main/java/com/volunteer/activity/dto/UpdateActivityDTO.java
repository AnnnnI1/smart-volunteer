package com.volunteer.activity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateActivityDTO {

    private String title;

    private String description;

    /** 活动所需技能（逗号分隔） */
    private String requiredSkills;

    @Min(value = 1, message = "总名额至少为1")
    private Integer totalQuota;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
}
