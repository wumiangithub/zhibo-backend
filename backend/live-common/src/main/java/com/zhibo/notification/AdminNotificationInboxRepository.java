package com.zhibo.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminNotificationInboxRepository
        extends JpaRepository<AdminNotificationInboxEntity, AdminNotificationInboxId> {

    long countByIdStaffIdAndReadTimeIsNull(long staffId);

    Optional<AdminNotificationInboxEntity> findByIdNotificationIdAndIdStaffId(
            String notificationId, long staffId);

    List<AdminNotificationInboxEntity> findByIdStaffIdAndIdNotificationIdIn(
            long staffId, Collection<String> notificationIds);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AdminNotificationInboxEntity i
            SET i.readTime = :readTime
            WHERE i.id.staffId = :staffId AND i.readTime IS NULL
            """)
    int markAllRead(@Param("staffId") long staffId, @Param("readTime") LocalDateTime readTime);
}
