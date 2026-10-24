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
import com.zhibo.common.UuidIds;

/** 素材分组表 {@code material_group}。 */
@Entity
@Table(name = "material_group")
@Getter
@Setter
public class MaterialGroupEntity {

    @Id
    @Column(length = 32)
    private String id;

    /** image / video / document / article */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 64)
    private String name;

    /** 该 type 下默认分组（不可删）。列名 is_default。 */
    @Column(name = "is_default", nullable = false)
    private boolean defaultGroup;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UuidIds.newId();
        }
    }
}
