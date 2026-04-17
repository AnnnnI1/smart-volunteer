package com.volunteer.common.enums;

public enum AppHttpCodeEnum {
    SUCCESS(200, "操作成功"),
    TOKEN_EXPIRED(400, "token无效或已过期"),
    NEED_LOGIN(401, "需要登录后操作"),
    NO_OPERATOR_AUTH(403, "无权限操作"),
    DUPLICATE_RECORD(410, "存在相同记录"),
    PARAM_ERROR(402, "参数错误"),
    OLD_PASSWORD_ERROR(405, "旧密码输入错误"),
    REQUEST_NOT_MATCH(406, "请求操作不符合规范"),
    SYSTEM_ERROR(500, "出现错误"),
    USERNAME_EXIST(501, "用户名已存在"),
    PHONENUMBER_EXIST(502, "手机号已存在"),
    EMAIL_EXIST(503, "邮箱已存在"),
    REQUIRE_USERNAME(504, "必需填写用户名"),
    LOGIN_ERROR(505, "用户名或密码错误"),
    ACTIVITY_NOT_FOUND(601, "活动不存在"),
    ACTIVITY_FULL(602, "活动名额已满"),
    ALREADY_REGISTERED(603, "您已报名该活动");


    int code;
    String msg;

    AppHttpCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
}
