package com.volunteer.user.controller;

import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean Validation (@Valid) 失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseResult handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, msg);
    }

    /** 类级别 @Validated 触发的约束异常 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseResult handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .findFirst().orElse("参数校验失败");
        return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, msg);
    }

    /** 数据库唯一索引冲突 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseResult handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "该邮箱或手机号已被占用");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public ResponseResult handleException(Exception e) {
        log.error("未知异常: ", e);
        return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR);
    }
}
