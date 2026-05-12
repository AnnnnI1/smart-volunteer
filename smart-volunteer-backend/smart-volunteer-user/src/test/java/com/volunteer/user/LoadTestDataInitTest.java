package com.volunteer.user;

import com.volunteer.common.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 压测前置数据初始化：
 * 1. 向 users 表批量插入 voltest001~voltest1000（BCrypt 加密，role=1）
 * 2. 向 vol_activity 表插入 id=9999 的测试活动（quota=100，status=1）
 * 3. 为 1000 个用户生成 JWT，写入项目根目录 vol_tokens.csv
 * 4. 用 Jedis 直连 Redis，初始化 activity:quota:9999 = 100
 */
public class LoadTestDataInitTest {

    // ── 数据库配置 ──────────────────────────────────────────────
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/smart_volunteer?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private static final String DB_USER = "userstest";
    private static final String DB_PASS = "123456";

    // ── Redis 配置 ──────────────────────────────────────────────
    private static final String REDIS_HOST = "localhost";
    private static final int    REDIS_PORT  = 6379;

    // ── 压测参数 ────────────────────────────────────────────────
    private static final int    USER_COUNT   = 1000;
    private static final long   ACTIVITY_ID  = 9999L;
    private static final int    TOTAL_QUOTA  = 100;

    // ── CSV 输出路径（smart-volunteer 项目根目录）──────────────
    // Maven 执行时 working dir = 模块目录，../../ 即 smart-volunteer/
    private static final String CSV_PATH =
            Paths.get("").toAbsolutePath()
                    .getParent()   // smart-volunteer-backend
                    .getParent()   // smart-volunteer
                    .resolve("vol_tokens.csv").toString();

    @Test
    void initLoadTestData() throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hashedPwd = encoder.encode("123456");

        List<Long> userIds = new ArrayList<>(USER_COUNT);

        // ── Step 1 & 2：数据库操作 ──────────────────────────────
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);

            // 1. 插入 1000 个测试用户（已存在则跳过）
            String insertUser = "INSERT IGNORE INTO users " +
                    "(username, nickname, password_hash, role, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 1, 1, NOW(), NOW())";
            try (PreparedStatement ps = conn.prepareStatement(
                    insertUser, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 1; i <= USER_COUNT; i++) {
                    String username = String.format("voltest%03d", i);
                    ps.setString(1, username);
                    ps.setString(2, username);
                    ps.setString(3, hashedPwd);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            System.out.println("[Step 1] 用户批量插入完成（已存在的自动跳过）");

            // 查询这 1000 个用户的 id（按 username 排序保证顺序）
            String queryIds = "SELECT id FROM users WHERE username REGEXP '^voltest[0-9]{3,4}$' " +
                    "ORDER BY username";
            try (PreparedStatement ps = conn.prepareStatement(queryIds);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    userIds.add(rs.getLong("id"));
                }
            }
            System.out.println("[Step 1] 查询到用户数：" + userIds.size());

            // 2. 插入测试活动 id=9999（已存在则先删除再插入，保证 quota 干净）
            conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM vol_activity WHERE id = " + ACTIVITY_ID);
            }
            String insertActivity = "INSERT INTO vol_activity " +
                    "(id, title, description, start_time, end_time, " +
                    " total_quota, joined_quota, status, organizer_id, create_time, update_time) " +
                    "VALUES (?, '[压测]高并发抢报测试活动', '仅用于JMeter压测，请勿手动报名', " +
                    "DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), " +
                    "?, 0, 1, NULL, NOW(), NOW())";
            try (PreparedStatement ps = conn.prepareStatement(insertActivity)) {
                ps.setLong(1, ACTIVITY_ID);
                ps.setInt(2, TOTAL_QUOTA);
                ps.executeUpdate();
            }
            System.out.println("[Step 2] 测试活动 id=" + ACTIVITY_ID + " 插入完成，quota=" + TOTAL_QUOTA);

            // 清理该活动的历史报名记录（保证名额干净）
            try (Statement st = conn.createStatement()) {
                int deleted = st.executeUpdate(
                        "DELETE FROM vol_registration WHERE activity_id = " + ACTIVITY_ID);
                System.out.println("[Step 2] 清理历史报名记录 " + deleted + " 条");
            }
        }

        // ── Step 3：生成 JWT 并写入 CSV ─────────────────────────
        System.out.println("[Step 3] 开始生成 JWT，写入：" + CSV_PATH);
        // TTL 设为 7 天，确保压测期间不过期；携带 role=1 claim
        long ttl7Days = 7L * 24 * 60 * 60 * 1000;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(CSV_PATH))) {
            for (Long uid : userIds) {
                // createJWT(subject, claims) 使用默认 TTL（24h），不够；
                // 直接用 createJWT(subject, ttlMillis) 生成 7 天有效期 token
                String token = JwtUtil.createJWT(String.valueOf(uid), ttl7Days);
                bw.write(token);
                bw.newLine();
            }
        }
        System.out.println("[Step 3] vol_tokens.csv 写入完成，共 " + userIds.size() + " 行");

        // ── Step 4：Redis 初始化名额键 ──────────────────────────
        // 直接用 Jedis 避免引入 Spring 上下文
        try (redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis(REDIS_HOST, REDIS_PORT)) {
            String key = "activity:quota:" + ACTIVITY_ID;
            jedis.set(key, String.valueOf(TOTAL_QUOTA));
            String val = jedis.get(key);
            System.out.println("[Step 4] Redis key=" + key + " 已设置为 " + val);
        }

        System.out.println("\n========== 压测数据初始化完成 ==========");
        System.out.println("  用户数：" + userIds.size());
        System.out.println("  活动ID：" + ACTIVITY_ID + "，名额：" + TOTAL_QUOTA);
        System.out.println("  CSV路径：" + CSV_PATH);
        System.out.println("  Redis key：activity:quota:" + ACTIVITY_ID + " = " + TOTAL_QUOTA);
    }
}
