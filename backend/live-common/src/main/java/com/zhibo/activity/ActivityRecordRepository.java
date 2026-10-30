package com.zhibo.activity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecordEntity, Long> {

    List<ActivityRecordEntity> findByActivityId(String activityId);

    /** 事件 6 可能无 webinar_id，按云侧 record_id 反查（通常一行）。 */
    List<ActivityRecordEntity> findByRecordId(Long recordId);

    Optional<ActivityRecordEntity> findByActivityIdAndProviderAndRecordId(
            String activityId, String provider, Long recordId);

    /** 启动补偿：status=downloading 且无 tos_url（null 或空串）。 */
    @Query("SELECT r FROM ActivityRecordEntity r WHERE r.status = :status "
            + "AND (r.tosUrl IS NULL OR r.tosUrl = '')")
    List<ActivityRecordEntity> findByStatusWithoutTos(@Param("status") Integer status);
}
