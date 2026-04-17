package com.volunteer.activity.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCache {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 设置字符串值 */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /** 获取字符串值 */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 原子递减，返回递减后的值 */
    public Long decrement(String key) {
        return stringRedisTemplate.opsForValue().decrement(key);
    }

    /** 原子递增，返回递增后的值 */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /** 删除 key */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /** 判断 key 是否存在 */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /** 仅当 key 不存在时设置（原子 SETNX），返回是否设置成功 */
    public Boolean setIfAbsent(String key, String value) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value);
    }
}
