package com.zhibo.material;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<MaterialEntity, String> {

    long countByGroupIdAndDeletedTimeIsNull(String groupId);

    boolean existsByObjectKeyAndDeletedTimeIsNull(String objectKey);

    /**
     * @param auditStatus 联合审核。null 不过滤。
     *                    2 驳回：任一方为 2（优先于待审核）；
     *                    0 待审核：没有驳回，且至少一方为 0；
     *                    1 通过：两方都是 1。
     * @param selectableOnly true 时仅 AI、风控两审均为 1（选择弹窗）；false 不过滤审核。
     */
    @Query("SELECT m FROM MaterialEntity m WHERE m.deletedTime IS NULL AND m.type = :type "
            + "AND (:groupId IS NULL OR m.groupId = :groupId) "
            + "AND (:keyword IS NULL OR m.name LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:selectableOnly = false OR (m.aiAuditStatus = 1 AND m.riskAuditStatus = 1)) "
            + "AND (:auditStatus IS NULL OR ("
            + "  (:auditStatus = 1 AND m.aiAuditStatus = 1 AND m.riskAuditStatus = 1) "
            + "  OR (:auditStatus = 2 AND (m.aiAuditStatus = 2 OR m.riskAuditStatus = 2)) "
            + "  OR (:auditStatus = 0 "
            + "      AND COALESCE(m.aiAuditStatus, 0) <> 2 "
            + "      AND COALESCE(m.riskAuditStatus, 0) <> 2 "
            + "      AND (COALESCE(m.aiAuditStatus, 0) = 0 "
            + "           OR COALESCE(m.riskAuditStatus, 0) = 0))"
            + ")) "
            + "ORDER BY m.createdTime DESC")
    Page<MaterialEntity> search(
            @Param("type") String type,
            @Param("groupId") String groupId,
            @Param("keyword") String keyword,
            @Param("auditStatus") Integer auditStatus,
            @Param("selectableOnly") boolean selectableOnly,
            Pageable pageable);
}
