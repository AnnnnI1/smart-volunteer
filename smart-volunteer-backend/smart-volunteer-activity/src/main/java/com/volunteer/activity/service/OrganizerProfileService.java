package com.volunteer.activity.service;

import com.volunteer.activity.entity.VolOrganizerProfile;

import java.util.Map;

public interface OrganizerProfileService {

    void ensureProfileExists(Long organizerId);

    void incrementSubmission(Long organizerId);

    void incrementRejected(Long organizerId);

    void incrementReport(Long organizerId);

    void incrementBypassAttempt(Long organizerId);

    void setForceManualReview(Long organizerId, java.time.LocalDateTime until);

    void updateRiskLevel(Long organizerId);

    VolOrganizerProfile getProfile(Long organizerId);

    Map<String, Object> getProfileSummary(Long organizerId);

    boolean shouldForceManualReview(Long organizerId);
}
