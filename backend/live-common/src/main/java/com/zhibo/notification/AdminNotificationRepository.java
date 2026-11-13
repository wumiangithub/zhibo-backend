package com.zhibo.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminNotificationRepository extends JpaRepository<AdminNotificationEntity, String> {

    @Query("""
            SELECT n FROM AdminNotificationEntity n
            WHERE EXISTS (
              SELECT 1 FROM AdminNotificationInboxEntity i
              WHERE i.id.notificationId = n.id AND i.id.staffId = :staffId
            )
            AND (:type IS NULL OR n.type = :type)
            AND (:auditResult IS NULL OR n.auditResult = :auditResult)
            AND (
              :keyword IS NULL OR :keyword = ''
              OR LOWER(n.category) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(n.body) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            ORDER BY n.createdTime DESC
            """)
    Page<AdminNotificationEntity> searchForStaff(
            @Param("staffId") long staffId,
            @Param("type") Integer type,
            @Param("auditResult") Integer auditResult,
            @Param("keyword") String keyword,
            Pageable pageable);
}
