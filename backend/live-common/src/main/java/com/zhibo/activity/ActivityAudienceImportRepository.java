package com.zhibo.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityAudienceImportRepository extends JpaRepository<ActivityAudienceImportEntity, Long> {

    List<ActivityAudienceImportEntity> findTop100ByActivityIdOrderByImportTimeDesc(String activityId);
}
