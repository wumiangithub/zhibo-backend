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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 活动回放文件表 {@code activity_record}（D88）。
 * 一行 = 一场活动 + 一个云侧 {@code record_id}；行主键不对外。
 * {@code status} 库列 INT：见下方常量。
 */
@Entity
@Table(name = "activity_record")
@Getter
@Setter
public class ActivityRecordEntity {

    /** 待打包：已入库，尚未成功提交微吼打包。 */
    public static final int STATUS_PENDING_PACKAGE = 1;
    /** 打包中：已调 record/download，等事件 6。 */
    public static final int STATUS_PACKAGING = 2;
    /** 下载/解压/传 TOS 中。 */
    public static final int STATUS_DOWNLOADING = 3;
    /** TOS 就绪。 */
    public static final int STATUS_READY = 4;
    /** 失败。 */
    public static final int STATUS_FAILED = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    /** 与 activity.provider 同：vhall / aliyun / tencent。 */
    @Column(nullable = false, length = 32)
    private String provider;

    /** 云侧回放 id（微吼 integer）。 */
    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(length = 256)
    private String title;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

    @Column(name = "storage_kb")
    private Long storageKb;

    /** 1 pending_package / 2 packaging / 3 downloading / 4 ready / 5 failed。 */
    @Column(nullable = false)
    private Integer status;

    @Column(name = "object_key", length = 1024)
    private String objectKey;

    @Column(name = "tos_url", length = 1024)
    private String tosUrl;

    @Column(name = "provider_created_time")
    private LocalDateTime providerCreatedTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
