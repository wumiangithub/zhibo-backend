package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActivityCoursewareRepository extends JpaRepository<ActivityCoursewareEntity, ActivityCoursewareId> {

    @Query("SELECT c FROM ActivityCoursewareEntity c WHERE c.id.activityId IN :ids ORDER BY c.id.activityId ASC, c.sortOrder ASC")
    List<ActivityCoursewareEntity> findByActivityIds(@Param("ids") Collection<String> ids);

    void deleteByIdActivityId(String activityId);
}
