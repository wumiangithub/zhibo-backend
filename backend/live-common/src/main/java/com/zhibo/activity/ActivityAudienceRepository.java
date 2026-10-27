package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityAudienceRepository
        extends JpaRepository<ActivityAudienceEntity, Long>, JpaSpecificationExecutor<ActivityAudienceEntity> {

    Optional<ActivityAudienceEntity> findByActivityIdAndViewerId(String activityId, Long viewerId);

    List<ActivityAudienceEntity> findByActivityIdAndViewerIdIn(String activityId, Collection<Long> viewerIds);

    long countByActivityId(String activityId);
}
