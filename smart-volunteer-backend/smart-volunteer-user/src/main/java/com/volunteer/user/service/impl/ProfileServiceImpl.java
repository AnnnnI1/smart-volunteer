package com.volunteer.user.service.impl;

import com.volunteer.common.entity.ResponseResult;
import com.volunteer.user.dto.ProfileUpdateDTO;
import com.volunteer.user.entity.VolProfile;
import com.volunteer.user.mapper.VolProfileMapper;
import com.volunteer.user.service.ProfileService;
import com.volunteer.user.vo.ProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ProfileServiceImpl implements ProfileService {

    @Autowired
    private VolProfileMapper profileMapper;

    @Override
    public ResponseResult getMyProfile(Long userId) {
        return getProfileByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult saveOrUpdateProfile(Long userId, ProfileUpdateDTO dto) {
        VolProfile existing = profileMapper.selectById(userId);

        if (Objects.isNull(existing)) {
            // 首次创建档案
            VolProfile profile = new VolProfile();
            profile.setUserId(userId);
            profile.setRealName(dto.getRealName());
            profile.setSkills(dto.getSkills());
            profile.setTotalHours(0);
            profileMapper.insert(profile);
        } else {
            // 更新已有档案（只更新可编辑字段，totalHours 由系统管理）
            if (dto.getRealName() != null) {
                existing.setRealName(dto.getRealName());
            }
            if (dto.getSkills() != null) {
                existing.setSkills(dto.getSkills());
            }
            profileMapper.updateById(existing);
        }

        return ResponseResult.okResult();
    }

    @Override
    public ResponseResult getProfileByUserId(Long userId) {
        VolProfile profile = profileMapper.selectById(userId);

        ProfileVO vo = new ProfileVO();
        vo.setUserId(userId);
        vo.setTotalHours(0);
        vo.setSkillsFromString(null);

        if (!Objects.isNull(profile)) {
            vo.setRealName(profile.getRealName());
            vo.setTotalHours(profile.getTotalHours() == null ? 0 : profile.getTotalHours());
            vo.setSkillsFromString(profile.getSkills());
        }

        return ResponseResult.okResult(vo);
    }
}
