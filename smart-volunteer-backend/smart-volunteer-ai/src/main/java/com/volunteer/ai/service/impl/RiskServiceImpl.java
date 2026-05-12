package com.volunteer.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.ai.service.RiskService;
import com.volunteer.ai.vo.RiskVO;
import com.volunteer.common.entity.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 志愿者流失预警服务
 *
 * 数据层（Java）：从 MySQL 聚合志愿者行为特征
 * 算法层（Python）：加权线性评分模型预测流失风险
 */
@Slf4j
@Service
public class RiskServiceImpl implements RiskService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${python.ai.url}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ResponseResult predictChurnRisk() {
        // 1. 聚合志愿者行为特征（SQL JOIN）
        String sql = """
            SELECT
                u.id           AS user_id,
                u.nickname,
                COUNT(r.id)                                    AS signup_count,
                SUM(CASE WHEN r.status = 1 THEN 1 ELSE 0 END) AS cancel_count,
                COALESCE(p.total_hours, 0)                     AS total_hours,
                COALESCE(
                    DATEDIFF(CURRENT_DATE, MAX(r.create_time)),
                    999
                )                                              AS inactive_days
            FROM users u
            LEFT JOIN vol_registration r ON r.user_id = u.id
            LEFT JOIN vol_profile      p ON p.user_id = u.id
            WHERE u.role = 1 AND u.status = 1
            GROUP BY u.id, u.nickname, p.total_hours
            HAVING signup_count > 0
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows.isEmpty()) {
            return ResponseResult.okResult(Collections.emptyList());
        }

        // 2. 构建 Python 请求体
        List<Map<String, Object>> volunteers = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> v = new HashMap<>();
            v.put("user_id",      row.get("user_id"));
            v.put("nickname",     row.get("nickname"));
            v.put("signup_count", row.get("signup_count") != null ? ((Number) row.get("signup_count")).intValue() : 0);
            v.put("cancel_count", row.get("cancel_count") != null ? ((Number) row.get("cancel_count")).intValue() : 0);
            v.put("total_hours",  row.get("total_hours")  != null ? ((Number) row.get("total_hours")).intValue()  : 0);
            v.put("inactive_days",row.get("inactive_days")!= null ? ((Number) row.get("inactive_days")).intValue(): 999);
            volunteers.add(v);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("volunteers", volunteers);

        // 3. 调用 Python AI 微服务
        try {
            WebClient client = WebClient.builder()
                    .baseUrl(pythonAiUrl)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String resp = client.post()
                    .uri("/ml/churn")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 4. 解析返回结果
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            List<RiskVO> results = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    RiskVO vo = new RiskVO();
                    vo.setUserId(item.get("userId").asLong());
                    vo.setNickname(item.get("nickname").asText());
                    vo.setSignupCount(item.get("signupCount").asInt());
                    vo.setCancelCount(item.get("cancelCount").asInt());
                    vo.setCancelRate(item.get("cancelRate").asDouble());
                    vo.setTotalHours(item.get("totalHours").asInt());
                    vo.setInactiveDays(item.get("inactiveDays").asInt());
                    vo.setRiskScore(item.get("riskScore").asDouble());
                    vo.setRiskLevel(item.get("riskLevel").asText());
                    vo.setRiskColor(item.get("riskColor").asText());
                    List<String> factors = new ArrayList<>();
                    item.get("riskFactors").forEach(f -> factors.add(f.asText()));
                    vo.setRiskFactors(factors);
                    results.add(vo);
                }
            }
            log.info("流失预警分析完成，共 {} 名志愿者", results.size());
            return ResponseResult.okResult(results);

        } catch (Exception e) {
            log.error("调用 Python 流失预警服务失败: {}", e.getMessage());
            return ResponseResult.errorResult(500, "预警服务暂不可用，请确认 Python AI 节点已启动");
        }
    }
}
