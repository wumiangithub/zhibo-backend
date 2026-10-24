package com.zhibo.material;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import com.zhibo.common.UuidIds;

/** 素材 AI / 风控审核流水。 */
@Entity
@Table(name = "material_audit")
@Getter
@Setter
public class MaterialAuditLogEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "material_id", nullable = false, length = 32)
    private String materialId;

    /** ai / risk */
    @Column(nullable = false, length = 16)
    private String action;

    /** passed / rejected */
    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 50)
    private String operatorName;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UuidIds.newId();
        }
    }
}
