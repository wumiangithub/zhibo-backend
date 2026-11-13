package com.zhibo.activity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLiveSessionRepository extends JpaRepository<ActivityLiveSessionEntity, Long> {

    List<ActivityLiveSessionEntity> findByActivityId(String activityId);

    Optional<ActivityLiveSessionEntity> findByActivityIdAndProviderAndSwitchId(
            String activityId, String provider, Long switchId);

    List<ActivityLiveSessionEntity> findByActivityIdAndEndTimeIsNull(String activityId);
}
