package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** 本场观众批量导入历史。 */
@Entity
@Table(name = "activity_audience_import")
@Getter
@Setter
public class ActivityAudienceImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    @Column(name = "importer_id", nullable = false)
    private Long importerId;

    @Column(name = "import_time", nullable = false)
    private LocalDateTime importTime;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    /** JSON 数组，保存本次导入全部失败行（不裁到 50）。 */
    @Column(name = "fail_details", columnDefinition = "MEDIUMTEXT")
    private String failDetails;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
