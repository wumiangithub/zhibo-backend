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

    /**
     * 对外课程列表：未软删，且满足可见性。
     * {@code status=1}：已上架且风控通过；{@code status=2}：已下架；
     * {@code status} 为空：二者并集。仍排除待上架、封禁、仅驳回等。
     * {@code idsEmpty=true} 时不过滤 id（{@code ids} 仍须传非空占位，避免 IN ()）。
     */
    @Query("SELECT c FROM CourseEntity c WHERE c.deletedTime IS NULL "
            + "AND (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')) "
            + "AND ("
            + "(:status IS NULL AND ((c.shelfStatus = 'on' AND c.riskStatus = 'passed') "
            + "OR c.shelfStatus = 'off')) "
            + "OR (:status = 1 AND c.shelfStatus = 'on' AND c.riskStatus = 'passed') "
            + "OR (:status = 2 AND c.shelfStatus = 'off')"
            + ") "
            + "AND (:idsEmpty = true OR c.id IN :ids)")
    Page<CourseEntity> searchOutside(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("idsEmpty") boolean idsEmpty,
            @Param("ids") Collection<String> ids,
            Pageable pageable);
}
