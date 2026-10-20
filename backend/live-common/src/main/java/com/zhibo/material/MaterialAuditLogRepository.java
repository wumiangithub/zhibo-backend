package com.zhibo.material;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaterialAuditLogRepository extends JpaRepository<MaterialAuditLogEntity, String> {
    List<MaterialAuditLogEntity> findByMaterialIdOrderByCreatedTimeDesc(String materialId);
}
