package com.zhibo.material;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** 素材元数据表 {@code material}；文件在 TOS。 */
@Entity
@Table(name = "material")
@Getter
@Setter
public class MaterialEntity {

    @Id
    @Column(length = 36)
    private String id;

    /** image / video / document / article */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId;

    @Column(nullable = false, length = 256)
    private String name;

    /** 文章可空 */
    @Column(length = 2048)
    private String url;

    /** 文章可空 */
    @Column(name = "object_key", length = 1024)
    private String objectKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(length = 128)
    private String meta;

    /** available / unavailable */
    @Column(nullable = false, length = 16)
    private String status = "available";

    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    @Column(name = "ai_audit_status", nullable = false)
    private Integer aiAuditStatus = MaterialAudit.PENDING;

    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    @Column(name = "risk_audit_status", nullable = false)
    private Integer riskAuditStatus = MaterialAudit.PENDING;

    /** 上传人 sys_useraccount.rs_mainkey_id；旧行可空 */
    @Column(name = "uploader_id")
    private Long uploaderId;

    /** 上传人姓名快照 */
    @Column(name = "uploader_name", length = 50)
    private String uploaderName;

    /** 所在部门展示名快照（多部门顿号） */
    @Column(name = "department_name", length = 256)
    private String departmentName;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    /** 删除人 sys_useraccount.rs_mainkey_id。 */
    @Column(name = "deleted_by")
    private Long deletedBy;

    /** 删除人姓名快照。 */
    @Column(name = "deleted_by_name", length = 50)
    private String deletedByName;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null || status.isBlank()) {
            status = "available";
        }
        if (aiAuditStatus == null) {
            aiAuditStatus = MaterialAudit.PENDING;
        }
        if (riskAuditStatus == null) {
            riskAuditStatus = MaterialAudit.PENDING;
        }
    }
}
