package com.volunteer.credit.service;

import com.volunteer.common.entity.ResponseResult;

public interface CreditService {

    /**
     * 变更积分（带幂等保护）
     * @param userId     用户ID
     * @param activityId 关联活动ID（可为null，如管理员调整）
     * @param delta      积分变化量（正数加分，负数扣分）
     * @param type       类型：1=报名奖励 2=完成奖励 3=取消扣分 4=管理员调整 5=现场签到奖励 6=缺席扣分
     * @param remark     备注
     * @return 是否实际写入（false表示幂等跳过）
     */
    boolean changePoints(Long userId, Long activityId, int delta, int type, String remark);

    /** 为某活动所有已签到用户发放完成奖励 */
    void awardAllForActivity(Long activityId);

    /** 为某活动所有缺席用户扣除缺席惩罚积分 */
    void penalizeAbsentForActivity(Long activityId);

    /** 查询用户积分余额 */
    ResponseResult getBalance(Long userId);

    /** 分页查询积分流水 */
    ResponseResult getRecords(Long userId, Integer page, Integer size);

    /** 管理员手动调整积分 */
    ResponseResult adminAdjust(Long userId, int delta, String remark);
}
