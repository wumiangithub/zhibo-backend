package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityAuditRepository extends JpaRepository<ActivityAuditEntity, String> {

    List<ActivityAuditEntity> findByActivityIdOrderByCreatedTimeDesc(String activityId);

    /** 按动作取审核流水（创建时间升序）。 */
    List<ActivityAuditEntity> findByActivityIdAndActionOrderByCreatedTimeAsc(String activityId, String action);
}
