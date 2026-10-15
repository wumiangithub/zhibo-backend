package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActivityProductRepository extends JpaRepository<ActivityProductEntity, ActivityProductId> {

    @Query("SELECT p FROM ActivityProductEntity p WHERE p.id.activityId IN :ids ORDER BY p.id.activityId ASC, p.sortOrder ASC")
    List<ActivityProductEntity> findByActivityIds(@Param("ids") Collection<String> ids);

    void deleteByIdActivityId(String activityId);
}
