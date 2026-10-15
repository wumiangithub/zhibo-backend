package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActivityHostRepository extends JpaRepository<ActivityHostEntity, ActivityHostId> {

    @Query("SELECT h FROM ActivityHostEntity h WHERE h.id.activityId IN :ids ORDER BY h.id.activityId ASC, h.sortOrder ASC")
    List<ActivityHostEntity> findByActivityIds(@Param("ids") Collection<String> ids);

    void deleteByIdActivityId(String activityId);
}
