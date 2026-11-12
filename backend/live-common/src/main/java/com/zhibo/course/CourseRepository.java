package com.zhibo.course;

import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<CourseEntity, String> {

    /**
     * 观众「我的学习 · 课程」：本站有观看权益（{@code course_user.entitlement_status=1}）、
     * 未软删，且未按售卖到期（days/fixed 且 expire ≤ now 则排除）。
     * 按学员行创建时间倒序。
     */
    @Query(
            value =
                    "SELECT c FROM CourseEntity c WHERE c.deletedTime IS NULL AND EXISTS ("
                            + "SELECT 1 FROM CourseUserEntity cu WHERE cu.courseId = c.id "
                            + "AND cu.username = :username AND cu.entitlementStatus = 1 "
                            + "AND (c.accessValidityType NOT IN ('days', 'fixed') "
                            + "OR cu.entitlementExpireTime IS NULL "
                            + "OR cu.entitlementExpireTime > :now)"
                            + ") ORDER BY (SELECT cu2.createdTime FROM CourseUserEntity cu2 "
                            + "WHERE cu2.courseId = c.id AND cu2.username = :username "
                            + "AND cu2.entitlementStatus = 1) DESC",
            countQuery =
                    "SELECT COUNT(c) FROM CourseEntity c WHERE c.deletedTime IS NULL AND EXISTS ("
                            + "SELECT 1 FROM CourseUserEntity cu WHERE cu.courseId = c.id "
                            + "AND cu.username = :username AND cu.entitlementStatus = 1 "
                            + "AND (c.accessValidityType NOT IN ('days', 'fixed') "
                            + "OR cu.entitlementExpireTime IS NULL "
                            + "OR cu.entitlementExpireTime > :now)"
                            + ")")
    Page<CourseEntity> searchEntitledForViewer(
            @Param("username") String username,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("SELECT c FROM CourseEntity c WHERE c.deletedTime IS NULL "
            + "AND (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:shelfStatus IS NULL OR c.shelfStatus = :shelfStatus) "
            + "AND (:riskStatus IS NULL OR c.riskStatus = :riskStatus)")
    Page<CourseEntity> search(
            @Param("keyword") String keyword,
            @Param("shelfStatus") String shelfStatus,
            @Param("riskStatus") String riskStatus,
            Pageable pageable);

    @Query("SELECT c FROM CourseEntity c WHERE c.deletedTime IS NULL AND c.id IN :ids "
            + "AND (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:shelfStatus IS NULL OR c.shelfStatus = :shelfStatus) "
            + "AND (:riskStatus IS NULL OR c.riskStatus = :riskStatus)")
    Page<CourseEntity> searchByIds(
            @Param("ids") Collection<String> ids,
            @Param("keyword") String keyword,
            @Param("shelfStatus") String shelfStatus,
            @Param("riskStatus") String riskStatus,
            Pageable pageable);
}
