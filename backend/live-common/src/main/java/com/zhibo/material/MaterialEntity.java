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

    /** image / video / document */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "object_key", nullable = false, length = 1024)
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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null || status.isBlank()) {
            status = "available";
        }
    }
}
