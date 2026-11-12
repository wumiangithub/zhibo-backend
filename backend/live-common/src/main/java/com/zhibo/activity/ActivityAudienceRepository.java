package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityAudienceRepository
        extends JpaRepository<ActivityAudienceEntity, Long>, JpaSpecificationExecutor<ActivityAudienceEntity> {

    Optional<ActivityAudienceEntity> findByActivityIdAndUsername(String activityId, String username);

    List<ActivityAudienceEntity> findByActivityIdAndUsernameIn(String activityId, Collection<String> usernames);

    long countByActivityId(String activityId);
}
