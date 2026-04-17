package com.volunteer.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.ai.entity.VolProfile;
import com.volunteer.ai.mapper.VolProfileMapper;
import com.volunteer.ai.service.KnnService;
import com.volunteer.ai.vo.ActivityRecommendVO;
import com.volunteer.ai.vo.KnnResultVO;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * KNN 志愿者匹配（调用 Python AI 微服务）
 *
 * 架构说明：
 *   Java 层负责数据查询与封装 → HTTP 调用 Python AI 节点
 *   Python 层负责算法计算（TF-IDF 加权余弦相似度 + 服务时长奖励）
 *   实现异构微服务算力调度，AI 计算与业务逻辑解耦
 */
@Slf4j
@Service
public class KnnServiceImpl implements KnnService {

    @Autowired
    private VolProfileMapper profileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${python.ai.url}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ResponseResult matchVolunteers(List<String> requiredSkills, int topK) {
        // 1. Java 层：查询数据库获取志愿者档案
        LambdaQueryWrapper<VolProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(VolProfile::getSkills).ne(VolProfile::getSkills, "");
        List<VolProfile> profiles = profileMapper.selectList(wrapper);

        if (profiles.isEmpty()) {
            return ResponseResult.okResult(Collections.emptyList());
        }

        // 2. 查询所有志愿者的积分余额（用于信誉权重）
        Map<Long, Integer> creditMap = fetchCreditMap();

        // 3. 查询出勤率
        Map<Long, Double> attendanceMap = fetchAttendanceMap();

        // 4. 构建发送给 Python 的请求体
        List<Map<String, Object>> volunteers = new ArrayList<>();
        for (VolProfile p : profiles) {
            Map<String, Object> v = new HashMap<>();
            v.put("userId",          p.getUserId());
            v.put("realName",        p.getRealName());
            v.put("skills",          p.getSkills());
            v.put("total_hours",     p.getTotalHours() == null ? 0 : p.getTotalHours());
            v.put("credit_balance",  creditMap.getOrDefault(p.getUserId(), 0));
            v.put("attendance_rate", attendanceMap.getOrDefault(p.getUserId(), 0.0));
            volunteers.add(v);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("requiredSkills", requiredSkills);
        requestBody.put("topK",          topK);
        requestBody.put("volunteers",    volunteers);

        // 4. HTTP 调用 Python AI 微服务
        try {
            WebClient client = WebClient.builder()
                    .baseUrl(pythonAiUrl)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String responseStr = client.post()
                    .uri("/ml/knn")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 5. 解析 Python 返回结果
            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode dataNode = root.get("data");
            List<KnnResultVO> results = new ArrayList<>();
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    KnnResultVO vo = new KnnResultVO();
                    vo.setUserId(item.get("userId").asLong());
                    vo.setRealName(item.get("realName").asText());
                    vo.setSkills(item.get("skills").asText());
                    vo.setTotalHours(item.get("totalHours").asInt());
                    vo.setCreditBalance(item.has("creditBalance") ? item.get("creditBalance").asInt() : 0);
                    vo.setSimilarity(item.get("similarity").asDouble());
                    vo.setHoursScore(item.has("hoursScore") ? item.get("hoursScore").asDouble() : 0.0);
                    vo.setCreditScore(item.has("creditScore") ? item.get("creditScore").asDouble() : 0.0);
                    vo.setAttendanceScore(item.has("attendanceScore") ? item.get("attendanceScore").asDouble() : 0.0);
                    vo.setAttendanceRate(item.has("attendanceRate") ? item.get("attendanceRate").asDouble() : 0.0);
                    vo.setFinalScore(item.get("finalScore").asDouble());
                    vo.setRank(item.get("rank").asInt());
                    List<String> matched = new ArrayList<>();
                    JsonNode ms = item.get("matchedSkills");
                    if (ms != null && ms.isArray()) {
                        ms.forEach(s -> matched.add(s.asText()));
                    }
                    vo.setMatchedSkills(matched);
                    results.add(vo);
                }
            }
            log.info("Python KNN 返回 {} 条结果", results.size());
            return ResponseResult.okResult(results);

        } catch (Exception e) {
            log.error("调用 Python AI 服务失败，降级为本地计算: {}", e.getMessage());
            return localFallback(requiredSkills, topK, profiles, creditMap, attendanceMap);
        }
    }

    /** 查询积分余额表，返回 userId → balance 映射 */
    private Map<Long, Integer> fetchCreditMap() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id, balance FROM vol_credit_balance");
            Map<Long, Integer> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long uid = ((Number) row.get("user_id")).longValue();
                Integer bal = ((Number) row.get("balance")).intValue();
                map.put(uid, bal);
            }
            return map;
        } catch (Exception e) {
            log.warn("查询积分余额失败，忽略信誉权重: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 查询出勤率：已签到次数 / (已报名+已签到+缺席 总次数) */
    private Map<Long, Double> fetchAttendanceMap() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id, " +
                    "SUM(CASE WHEN status=2 THEN 1 ELSE 0 END) AS checkin_cnt, " +
                    "COUNT(*) AS total_cnt " +
                    "FROM vol_registration WHERE status IN (0,2,4) GROUP BY user_id");
            Map<Long, Double> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long uid = ((Number) row.get("user_id")).longValue();
                long checkin = ((Number) row.get("checkin_cnt")).longValue();
                long total   = ((Number) row.get("total_cnt")).longValue();
                map.put(uid, total == 0 ? 0.0 : (double) checkin / total);
            }
            return map;
        } catch (Exception e) {
            log.warn("查询出勤率失败，忽略出勤权重: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 降级方案：Python 不可用时使用 Java 本地余弦相似度 + 积分信誉 + 出勤加成 */
    private ResponseResult localFallback(List<String> requiredSkills, int topK,
                                         List<VolProfile> profiles, Map<Long, Integer> creditMap,
                                         Map<Long, Double> attendanceMap) {
        Set<String> vocabularySet = new LinkedHashSet<>(requiredSkills);
        for (VolProfile p : profiles) vocabularySet.addAll(splitSkills(p.getSkills()));
        List<String> vocabulary = new ArrayList<>(vocabularySet);
        double[] queryVec = toVector(requiredSkills, vocabulary);

        int maxCredit = creditMap.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        if (maxCredit <= 0) maxCredit = 1;

        List<KnnResultVO> results = new ArrayList<>();
        for (VolProfile profile : profiles) {
            List<String> volSkills = splitSkills(profile.getSkills());
            double sim = cosineSimilarity(queryVec, toVector(volSkills, vocabulary));
            int credit = creditMap.getOrDefault(profile.getUserId(), 0);
            double creditBonus = (double) credit / maxCredit * 0.15;
            double attRate = attendanceMap.getOrDefault(profile.getUserId(), 0.0);
            double attBonus = attRate * 0.20;
            KnnResultVO vo = new KnnResultVO();
            vo.setUserId(profile.getUserId());
            vo.setRealName(profile.getRealName());
            vo.setSkills(profile.getSkills());
            vo.setTotalHours(profile.getTotalHours() == null ? 0 : profile.getTotalHours());
            vo.setCreditBalance(credit);
            vo.setSimilarity(Math.round(sim * 10000.0) / 10000.0);
            vo.setHoursScore(0.0);
            vo.setCreditScore(Math.round(creditBonus * 10000.0) / 10000.0);
            vo.setAttendanceRate(Math.round(attRate * 10000.0) / 10000.0);
            vo.setAttendanceScore(Math.round(attBonus * 10000.0) / 10000.0);
            vo.setFinalScore(Math.round((sim + creditBonus + attBonus) * 10000.0) / 10000.0);
            vo.setMatchedSkills(volSkills.stream().filter(requiredSkills::contains).toList());
            results.add(vo);
        }
        results.sort(Comparator.comparingDouble(KnnResultVO::getFinalScore).reversed());
        for (int i = 0; i < results.size(); i++) results.get(i).setRank(i + 1);
        return ResponseResult.okResult(results.stream().limit(Math.max(topK, 1)).toList());
    }

    private List<String> splitSkills(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        return Arrays.asList(s.split(","));
    }
    private double[] toVector(List<String> skills, List<String> vocab) {
        Set<String> sk = new HashSet<>(skills);
        double[] v = new double[vocab.size()];
        for (int i = 0; i < vocab.size(); i++) v[i] = sk.contains(vocab.get(i)) ? 1.0 : 0.0;
        return v;
    }
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // -----------------------------------------------------------------------
    // 活动推荐（志愿者用）：根据自身技能，KNN 找最匹配的活动
    // -----------------------------------------------------------------------
    @Override
    public ResponseResult recommendActivities(Long userId, int topK) {
        // 1. 查询当前志愿者技能
        String skillSql = "SELECT skills FROM vol_profile WHERE user_id = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(skillSql, userId);
        if (rows.isEmpty() || rows.get(0).get("skills") == null
                || rows.get(0).get("skills").toString().isBlank()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR,
                    "请先在个人中心完善技能档案，才能获得智能推荐");
        }
        String userSkills = rows.get(0).get("skills").toString();
        List<String> userSkillList = Arrays.asList(userSkills.split(","));

        // 2. 查询所有有 required_skills 的活动（报名中 status=1 或未开始 status=0）
        String actSql = """
            SELECT id, title, description, required_skills, status,
                   total_quota, joined_quota,
                   (total_quota - joined_quota) AS remain_quota,
                   start_time, end_time
            FROM vol_activity
            WHERE required_skills IS NOT NULL AND required_skills != ''
              AND status IN (0, 1)
            """;
        List<Map<String, Object>> activities = jdbcTemplate.queryForList(actSql);
        if (activities.isEmpty()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR,
                    "暂无设置所需技能的活动，请联系管理员完善活动信息");
        }

        // 3. 复用 Python /ml/knn：把"活动的 required_skills"当作候选的"志愿者 skills"
        //    用户的 skills 作为 requiredSkills 查询向量，找最匹配的活动
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> act : activities) {
            Map<String, Object> c = new HashMap<>();
            c.put("userId",     act.get("id"));
            c.put("realName",   act.get("title"));
            c.put("skills",     act.get("required_skills"));
            c.put("total_hours", 0);
            candidates.add(c);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("requiredSkills", userSkillList);
        requestBody.put("topK",          topK);
        requestBody.put("volunteers",    candidates);

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(pythonAiUrl)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String resp = client.post()
                    .uri("/ml/knn")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 4. 解析结果并关联活动详情
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            List<ActivityRecommendVO> results = new ArrayList<>();

            // 建活动 id→详情 map 方便关联
            Map<Long, Map<String, Object>> actMap = new HashMap<>();
            for (Map<String, Object> act : activities) {
                actMap.put(((Number) act.get("id")).longValue(), act);
            }

            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    Long actId = item.get("userId").asLong();
                    Map<String, Object> actDetail = actMap.get(actId);
                    if (actDetail == null) continue;

                    ActivityRecommendVO vo = new ActivityRecommendVO();
                    vo.setActivityId(actId);
                    vo.setTitle(item.get("realName").asText());
                    vo.setRequiredSkills(actDetail.get("required_skills").toString());
                    vo.setStatus(((Number) actDetail.get("status")).intValue());
                    vo.setTotalQuota(((Number) actDetail.get("total_quota")).intValue());
                    vo.setJoinedQuota(((Number) actDetail.get("joined_quota")).intValue());
                    vo.setRemainQuota(((Number) actDetail.get("remain_quota")).intValue());
                    vo.setStartTime(actDetail.get("start_time") != null ? actDetail.get("start_time").toString() : "");
                    vo.setSimilarity(item.get("similarity").asDouble());
                    vo.setRank(item.get("rank").asInt());
                    List<String> matched = new ArrayList<>();
                    JsonNode ms = item.get("matchedSkills");
                    if (ms != null && ms.isArray()) ms.forEach(s -> matched.add(s.asText()));
                    vo.setMatchedSkills(matched);
                    results.add(vo);
                }
            }
            log.info("活动推荐完成，userId={} 推荐 {} 个活动", userId, results.size());
            return ResponseResult.okResult(results);

        } catch (Exception e) {
            log.error("活动推荐调用 Python 失败: {}", e.getMessage());
            return ResponseResult.errorResult(500, "推荐服务暂不可用");
        }
    }

    // -----------------------------------------------------------------------
    // 双阶段复合推荐引擎（Transformer 向量召回 + DeepSeek Agent 精排）
    // -----------------------------------------------------------------------
    @Override
    public ResponseResult hybridRecommend(Long userId, Long activityId) {
        // 1. 查询活动详情
        List<Map<String, Object>> actRows = jdbcTemplate.queryForList(
                "SELECT id, title, description, required_skills FROM vol_activity WHERE id = ?",
                activityId);
        if (actRows.isEmpty()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.ACTIVITY_NOT_FOUND);
        }
        Map<String, Object> activity = actRows.get(0);
        String activityTitle = activity.get("title") != null ? activity.get("title").toString() : "";
        String activityDesc = activity.get("description") != null ? activity.get("description").toString() : "";
        String requiredSkillsStr = activity.get("required_skills") != null ? activity.get("required_skills").toString() : "";
        List<String> requiredSkills = Arrays.asList(requiredSkillsStr.split(",")).stream()
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        // 2. 查询当前用户档案
        String profileSql = "SELECT skills, real_name FROM vol_profile WHERE user_id = ?";
        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList(profileSql, userId);
        List<String> userSkills = new java.util.ArrayList<>();
        String userProfile = "";
        if (!profileRows.isEmpty() && profileRows.get(0).get("skills") != null) {
            String skillsStr = profileRows.get(0).get("skills").toString();
            userSkills = new java.util.ArrayList<>(Arrays.asList(skillsStr.split(",")).stream()
                    .map(String::trim).filter(s -> !s.isBlank()).toList());
            Object rn = profileRows.get(0).get("real_name");
            userProfile = rn != null ? rn.toString() : "";
        }

        // 3. 查询候选人（所有有档案的志愿者，排除自己，且排除已报名该活动的用户）
        List<Map<String, Object>> candidateRows = jdbcTemplate.queryForList(
                "SELECT vp.user_id, vp.real_name, vp.skills, vp.total_hours, vcb.balance as credit_balance " +
                "FROM vol_profile vp " +
                "LEFT JOIN vol_credit_balance vcb ON vp.user_id = vcb.user_id " +
                "WHERE vp.user_id != ? AND vp.skills IS NOT NULL AND vp.skills != '' " +
                "AND vp.user_id NOT IN (" +
                "  SELECT user_id FROM vol_registration WHERE activity_id = ? AND status IN (0, 2)" +
                ")",
                userId, activityId);

        List<Map<String, Object>> candidates = new java.util.ArrayList<>();
        Map<Long, Map<String, Object>> candidateMap = new java.util.HashMap<>();
        for (Map<String, Object> row : candidateRows) {
            Long uid = ((Number) row.get("user_id")).longValue();
            String skills = row.get("skills") != null ? row.get("skills").toString() : "";
            int hours = row.get("total_hours") != null ? ((Number) row.get("total_hours")).intValue() : 0;
            int credit = row.get("credit_balance") != null ? ((Number) row.get("credit_balance")).intValue() : 0;
            String realName = row.get("real_name") != null ? row.get("real_name").toString() : "匿名";
            Map<String, Object> c = new java.util.HashMap<>();
            c.put("user_id", uid);
            c.put("real_name", realName);
            c.put("skills", skills);
            c.put("total_hours", hours);
            c.put("credit_balance", credit);
            candidates.add(c);
            candidateMap.put(uid, c);
        }

        if (candidates.isEmpty()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "暂无可推荐的候选人");
        }

        // 4. 调用 Python 双阶段推荐引擎
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("user_id", userId);
        requestBody.put("user_skills", userSkills);
        requestBody.put("user_profile", userProfile);
        requestBody.put("required_skills", requiredSkills);
        requestBody.put("activity_title", activityTitle);
        requestBody.put("activity_description", activityDesc);
        requestBody.put("candidates", candidates);
        requestBody.put("top_k", 3);

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(pythonAiUrl)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String resp = client.post()
                    .uri("/ai/recommend")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(90))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            if (data == null) {
                return ResponseResult.errorResult(500, "推荐引擎返回数据异常");
            }

            // 解析候选人列表
            List<Map<String, Object>> resultCandidates = new java.util.ArrayList<>();
            JsonNode candNode = data.get("candidates");
            if (candNode != null && candNode.isArray()) {
                for (JsonNode cn : candNode) {
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("userId", cn.has("user_id") ? cn.get("user_id").asLong() : 0L);
                    item.put("realName", cn.has("real_name") ? cn.get("real_name").asText() : "");
                    item.put("skills", cn.has("skills") ? cn.get("skills").asText() : "");
                    item.put("totalHours", cn.has("total_hours") ? cn.get("total_hours").asInt() : 0);
                    item.put("similarityScore", cn.has("similarity_score") ? cn.get("similarity_score").asDouble() : 0.0);
                    item.put("rank", cn.has("rank") ? cn.get("rank").asInt() : 0);
                    resultCandidates.add(item);
                }
            }

            // 解析 AI 推荐理由
            Map<String, Object> finalRecommendation = new java.util.HashMap<>();
            JsonNode recNode = data.get("final_recommendation");
            if (recNode != null) {
                finalRecommendation.put("recommendedUserId", recNode.has("recommended_user_id") ? recNode.get("recommended_user_id").asLong() : 0L);
                finalRecommendation.put("recommendedUserName", recNode.has("recommended_user_name") ? recNode.get("recommended_user_name").asText() : "");
                finalRecommendation.put("aiReasoning", recNode.has("ai_reasoning") ? recNode.get("ai_reasoning").asText() : "");
                finalRecommendation.put("rankingCriteria", recNode.has("ranking_criteria") ? recNode.get("ranking_criteria").asText() : "");
            }

            String stage = data.has("stage") ? data.get("stage").asText() : "unknown";
            String modelInfo = data.has("model_info") ? data.get("model_info").asText() : "";

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("candidates", resultCandidates);
            result.put("finalRecommendation", finalRecommendation);
            result.put("stage", stage);
            result.put("modelInfo", modelInfo);

            log.info("双阶段推荐完成 userId={} activityId={} stage={}", userId, activityId, stage);
            return ResponseResult.okResult(result);

        } catch (Exception e) {
            log.error("双阶段推荐引擎调用失败 userId={} activityId={} error={}", userId, activityId, e.getMessage());
            return ResponseResult.errorResult(500, "AI推荐服务暂时不可用，请稍后重试");
        }
    }

    // -----------------------------------------------------------------------
    // Feed 流个性化推荐（行为隐式反馈 + Transformer 向量融合 + DeepSeek 推荐语）
    // -----------------------------------------------------------------------
    @Override
    public ResponseResult feedRecommend(Long userId, int page, int pageSize, Integer statusFilter) {
        // ── 1. 查询用户技能画像 ──────────────────────────────────────────
        List<Map<String, Object>> profileRows = jdbcTemplate.queryForList(
                "SELECT skills FROM vol_profile WHERE user_id = ?", userId);
        String userSkillsStr = "";
        if (!profileRows.isEmpty()) {
            userSkillsStr = profileRows.get(0).get("skills") != null
                    ? profileRows.get(0).get("skills").toString() : "";
        }
        // profile_text 字段不存在，用 skills 文本作为画像描述
        String userProfile = userSkillsStr;
        List<String> userSkills = userSkillsStr.isBlank()
                ? new ArrayList<>()
                : Arrays.asList(userSkillsStr.split(","));

        // ── 2. 查询历史行为活动（按行为类型分权重）─────────────────────
        //    行为类型：completed(完成) > checked_in(签到) > registered(报名)
        String historySql = """
            SELECT va.title, va.description,
                   CASE
                     WHEN vr.status = 2 AND va.status = 3 THEN 'completed'
                     WHEN vr.status = 2                   THEN 'checked_in'
                     ELSE 'registered'
                   END AS behavior_type
            FROM vol_registration vr
            JOIN vol_activity va ON vr.activity_id = va.id
            WHERE vr.user_id = ? AND vr.status IN (0, 2)
            ORDER BY
                CASE WHEN vr.status = 2 AND va.status = 3 THEN 0
                     WHEN vr.status = 2 THEN 1
                     ELSE 2 END,
                vr.id DESC
            LIMIT 20
            """;
        List<Map<String, Object>> historyRows = jdbcTemplate.queryForList(historySql, userId);
        List<Map<String, Object>> historyActivities = new ArrayList<>();
        for (Map<String, Object> row : historyRows) {
            Map<String, Object> h = new HashMap<>();
            h.put("title",         row.get("title") != null ? row.get("title").toString() : "");
            h.put("description",   row.get("description") != null ? row.get("description").toString() : "");
            h.put("behavior_type", row.get("behavior_type").toString());
            historyActivities.add(h);
        }

        // ── 3. 查询已报名的活动 ID（过滤用）────────────────────────────
        List<Long> registeredIds = jdbcTemplate.queryForList(
                "SELECT activity_id FROM vol_registration WHERE user_id = ? AND status IN (0, 2)",
                Long.class, userId);
        Set<Long> registeredSet = new HashSet<>(registeredIds);

        // ── 4. 构建活动池（过滤已报名，按 statusFilter 筛选）───────────
        String poolSql;
        List<Object> poolParams = new ArrayList<>();
        if (statusFilter != null) {
            poolSql = """
                SELECT va.id, va.title, va.description, va.required_skills,
                       va.status, va.start_time, va.total_quota, va.joined_quota,
                       (va.total_quota - va.joined_quota) AS remain_quota,
                       u.nickname AS organizer_name
                FROM vol_activity va
                LEFT JOIN users u ON va.organizer_id = u.id
                WHERE va.audit_status = 1 AND va.status = ?
                ORDER BY va.id DESC
                LIMIT 200
                """;
            poolParams.add(statusFilter);
        } else {
            poolSql = """
                SELECT va.id, va.title, va.description, va.required_skills,
                       va.status, va.start_time, va.total_quota, va.joined_quota,
                       (va.total_quota - va.joined_quota) AS remain_quota,
                       u.nickname AS organizer_name
                FROM vol_activity va
                LEFT JOIN users u ON va.organizer_id = u.id
                WHERE va.audit_status = 1 AND va.status IN (0, 1)
                ORDER BY va.id DESC
                LIMIT 200
                """;
        }
        List<Map<String, Object>> poolRows = jdbcTemplate.queryForList(poolSql, poolParams.toArray());

        List<Map<String, Object>> activityPool = new ArrayList<>();
        for (Map<String, Object> row : poolRows) {
            Long actId = ((Number) row.get("id")).longValue();
            if (registeredSet.contains(actId)) continue;  // 过滤已报名
            Map<String, Object> a = new HashMap<>();
            a.put("id",             actId);
            a.put("title",          row.get("title") != null ? row.get("title").toString() : "");
            a.put("description",    row.get("description") != null ? row.get("description").toString() : "");
            a.put("required_skills",row.get("required_skills") != null ? row.get("required_skills").toString() : "");
            a.put("status",         ((Number) row.get("status")).intValue());
            a.put("start_time",     row.get("start_time") != null ? row.get("start_time").toString() : "");
            a.put("total_quota",    row.get("total_quota") != null ? ((Number)row.get("total_quota")).intValue() : 0);
            a.put("joined_quota",   row.get("joined_quota") != null ? ((Number)row.get("joined_quota")).intValue() : 0);
            a.put("remain_quota",   row.get("remain_quota") != null ? ((Number)row.get("remain_quota")).intValue() : 0);
            a.put("organizer_name", row.get("organizer_name") != null ? row.get("organizer_name").toString() : "");
            activityPool.add(a);
        }

        if (activityPool.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("user_id", userId); empty.put("page", page); empty.put("page_size", pageSize);
            empty.put("total", 0); empty.put("has_more", false); empty.put("items", new ArrayList<>());
            empty.put("vector_mode", "empty");
            return ResponseResult.okResult(empty);
        }

        // ── 5. 调用 Python /ai/feed ──────────────────────────────────
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("user_id",           userId);
        requestBody.put("user_skills",        userSkills);
        requestBody.put("user_profile",       userProfile);
        requestBody.put("history_activities", historyActivities);
        requestBody.put("activity_pool",      activityPool);
        requestBody.put("page",              page);
        requestBody.put("page_size",         pageSize);

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(pythonAiUrl)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String resp = client.post()
                    .uri("/ai/feed")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");

            log.info("Feed推荐完成 userId={} page={} total={} hasMore={}",
                    userId, page,
                    data != null ? data.get("total") : 0,
                    data != null ? data.get("has_more") : false);

            // 直接透传 Python 返回的 data 对象
            return ResponseResult.okResult(objectMapper.convertValue(data, Object.class));

        } catch (Exception e) {
            log.error("Feed推荐调用Python失败 userId={} error={}", userId, e.getMessage());
            return ResponseResult.errorResult(500, "推荐服务暂时不可用，请稍后重试");
        }
    }
}
