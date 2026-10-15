package com.zhibo.material;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<MaterialEntity, String> {

    long countByGroupIdAndDeletedAtIsNull(String groupId);

    boolean existsByObjectKeyAndDeletedAtIsNull(String objectKey);

    /**
     * @param selectableOnly true 时仅 AI、风控两审均为 passed（选择弹窗）；false 不过滤审核。
     */
    @Query("SELECT m FROM MaterialEntity m WHERE m.deletedAt IS NULL AND m.type = :type "
            + "AND (:groupId IS NULL OR m.groupId = :groupId) "
            + "AND (:keyword IS NULL OR m.name LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:selectableOnly = false OR (m.aiAuditStatus = 'passed' AND m.riskAuditStatus = 'passed')) "
            + "ORDER BY m.createdAt DESC")
    Page<MaterialEntity> search(
            @Param("type") String type,
            @Param("groupId") String groupId,
            @Param("keyword") String keyword,
            @Param("selectableOnly") boolean selectableOnly,
            Pageable pageable);
}
