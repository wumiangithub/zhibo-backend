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

    /** 是否单独售卖。 */
    @Column(name = "sell_alone", nullable = false)
    private Boolean sellAlone = Boolean.TRUE;

    /** free / encrypted / specified。 */
    @Column(name = "sell_type", nullable = false, length = 16)
    private String sellType = "free";

    /** 列表有效期文案。 */
    @Column(name = "validity_label", nullable = false, length = 32)
    private String validityLabel = "长期有效";

    /** 加密售卖密码 AES 密文。 */
    @Column(name = "sell_password_cipher", length = 512)
    private String sellPasswordCipher;

    /** long / days（权益生效后 N 天）/ fixed（固定时间到期）。 */
    @Column(name = "access_validity_type", nullable = false, length = 16)
    private String accessValidityType = "long";

    /** 权益生效后 N 天。 */
    @Column(name = "access_validity_days")
    private Integer accessValidityDays;

    /** 固定到期时刻；type=fixed 时有值。 */
    @Column(name = "access_validity_fixed_time")
    private LocalDateTime accessValidityFixedTime;

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
