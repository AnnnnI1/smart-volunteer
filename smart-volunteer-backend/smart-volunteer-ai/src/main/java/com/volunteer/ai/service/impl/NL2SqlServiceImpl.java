package com.volunteer.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.ai.service.NL2SqlService;
import com.volunteer.ai.vo.NL2SqlVO;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NL2SQL 服务（论文创新点一：AI 智能数据中台）
 *
 * 流程：
 * 1. 构建携带数据库 Schema 的 System Prompt
 * 2. 将用户自然语言问题发送给 DeepSeek API
 * 3. 从响应中提取 SQL 语句
 * 4. 安全校验（只允许 SELECT，禁止写操作）
 * 5. 用 JdbcTemplate 执行 SQL 并返回结果
 */
@Slf4j
@Service
public class NL2SqlServiceImpl implements NL2SqlService {

    private static final String SCHEMA_PROMPT = """
            你是一个 MySQL 专家。根据用户的自然语言描述，生成对应的 SQL 查询语句。

            数据库名称：smart_volunteer

            表结构如下：

            1. users（系统用户表）
               - id BIGINT 主键
               - username VARCHAR 用户名
               - nickname VARCHAR 昵称
               - email VARCHAR 邮箱
               - phone VARCHAR 手机号
               - role INT（0=管理员, 1=志愿者, 2=组织者）
               - status INT（1=正常, 0=禁用）
               - created_at DATETIME 注册时间

            2. vol_activity（志愿活动表）
               - id BIGINT 主键
               - title VARCHAR 活动标题
               - description VARCHAR 活动描述
               - required_skills VARCHAR 所需技能（逗号分隔）
               - total_quota INT 总名额
               - joined_quota INT 已报名人数
               - status INT（0=未开始, 1=报名中, 2=进行中, 3=已结束）
               - organizer_id BIGINT 发起人用户ID（关联users.id，NULL表示管理员创建）
               - start_time DATETIME 开始时间
               - end_time DATETIME 结束时间
               - create_time DATETIME

            3. vol_registration（报名记录表）
               - id BIGINT 主键
               - user_id BIGINT 关联users.id
               - activity_id BIGINT 关联vol_activity.id
               - status INT（0=已报名, 1=已取消, 2=已签到, 4=已缺席）
               - create_time DATETIME

            4. vol_profile（志愿者档案表）
               - user_id BIGINT 主键，关联users.id
               - real_name VARCHAR 真实姓名
               - skills VARCHAR 技能标签（逗号分隔，如"医疗,教育"）
               - total_hours INT 累计服务时长（小时）

            5. vol_checkin_code（签到码表）
               - id BIGINT 主键
               - activity_id BIGINT 关联vol_activity.id
               - code VARCHAR 6位数字签到码
               - create_time DATETIME
               - is_active TINYINT（1=有效, 0=已失效）

            要求：
            - 只输出 SQL 语句本身，不要任何解释
            - 只能生成 SELECT 语句
            - SQL 末尾不要加分号
            - 字符串值用单引号
            - 如果需要当前时间用 NOW()
            """;

    @Autowired
    private WebClient deepSeekWebClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${deepseek.api.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ResponseResult query(String query) {
        try {
            // 1. 调用 DeepSeek API 生成 SQL
            String sql = callDeepSeek(query);
            log.info("NL2SQL 生成 SQL: {}", sql);

            // 2. 安全校验：只允许 SELECT
            String trimmed = sql.trim().toUpperCase();
            if (!trimmed.startsWith("SELECT")) {
                return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR,
                        "生成的 SQL 非查询语句，已拒绝执行");
            }

            // 3. 执行查询（LIMIT 兜底，防止全表扫描）
            String safeSql = appendLimit(sql);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(safeSql);

            NL2SqlVO vo = new NL2SqlVO();
            vo.setSql(sql);
            vo.setData(rows);
            vo.setTotal(rows.size());
            return ResponseResult.okResult(vo);

        } catch (Exception e) {
            log.error("NL2SQL 执行失败", e);
            String friendlyMsg = isTimeoutOrNetwork(e)
                    ? "语义理解超时，请稍后重试"
                    : "查询执行失败，请检查问题描述后重试";
            return ResponseResult.errorResult(AppHttpCodeEnum.SYSTEM_ERROR, friendlyMsg);
        }
    }

    /** 调用 DeepSeek API，返回生成的 SQL 文本 */
    private String callDeepSeek(String userQuery) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", SCHEMA_PROMPT),
                Map.of("role", "user", "content", userQuery)
        ));
        requestBody.put("temperature", 0.1);  // 低温度保证 SQL 稳定输出

        String response = deepSeekWebClient.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // 解析 choices[0].message.content
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();
            return extractSql(content);
        } catch (Exception e) {
            throw new RuntimeException("DeepSeek 响应解析失败: " + response, e);
        }
    }

    /** 从 Markdown 代码块或纯文本中提取 SQL */
    private String extractSql(String content) {
        content = content.trim();
        // 去掉 ```sql ... ``` 包裹
        if (content.contains("```")) {
            int start = content.indexOf("```");
            int end = content.lastIndexOf("```");
            if (start != end) {
                content = content.substring(start + 3, end).trim();
                // 去掉语言标识符行（如 "sql\n"）
                if (content.startsWith("sql")) {
                    content = content.substring(3).trim();
                }
            }
        }
        // 去掉末尾分号
        return content.endsWith(";") ? content.substring(0, content.length() - 1).trim() : content;
    }

    /** 如果 SQL 没有 LIMIT，自动追加 LIMIT 200 防止大表扫描 */
    private String appendLimit(String sql) {
        if (!sql.toUpperCase().contains("LIMIT")) {
            return sql + " LIMIT 200";
        }
        return sql;
    }

    /** 判断是否为超时或网络异常 */
    private boolean isTimeoutOrNetwork(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("timeout") || msg.contains("connection")
                || msg.contains("timed out")
                || e.getCause() instanceof java.util.concurrent.TimeoutException;
    }
}
