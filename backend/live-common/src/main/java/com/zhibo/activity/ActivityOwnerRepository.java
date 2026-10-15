package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActivityOwnerRepository extends JpaRepository<ActivityOwnerEntity, ActivityOwnerId> {

    @Query("SELECT o FROM ActivityOwnerEntity o WHERE o.id.activityId IN :ids ORDER BY o.id.activityId ASC, o.sortOrder ASC")
    List<ActivityOwnerEntity> findByActivityIds(@Param("ids") Collection<String> ids);

    void deleteByIdActivityId(String activityId);
}
