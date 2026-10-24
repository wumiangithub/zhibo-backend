package com.zhibo.course;

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

/** 课程表 {@code course}。 */
@Entity
@Table(name = "course")
@Getter
@Setter
public class CourseEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 45)
    private String title;

    @Column(length = 256)
    private String introduction;

    @Column(name = "detail_html", columnDefinition = "MEDIUMTEXT")
    private String detailHtml;

    @Column(name = "cover_material_id", nullable = false, length = 32)
    private String coverMaterialId;

    @Column(name = "cover_url", nullable = false, length = 2048)
    private String coverUrl;

    @Column(name = "auto_list", nullable = false)
    private Boolean autoList;

    /** pending / on / off / banned */
    @Column(name = "shelf_status", nullable = false, length = 16)
    private String shelfStatus;

    /** pending / passed / rejected */
    @Column(name = "risk_status", nullable = false, length = 16)
    private String riskStatus;

    @Column(name = "content_count", nullable = false)
    private int contentCount;

    @Column(name = "entitled_user_count", nullable = false)
    private int entitledUserCount;

    @Column(name = "total_user_count", nullable = false)
    private int totalUserCount;

    @Column(name = "sales_count", nullable = false)
    private int salesCount;

    @Column(name = "listed_time")
    private LocalDateTime listedTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null || id.isBlank()) {
            id = UuidIds.newId();
        }
    }
}
