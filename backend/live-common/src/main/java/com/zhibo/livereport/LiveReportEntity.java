package com.zhibo.livereport;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "live_report") @Getter @Setter
public class LiveReportEntity {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 64) private String title;
    @Column(nullable = false, length = 500) private String content;
    @Column(name = "plan_times", nullable = false, columnDefinition = "json") private String planTimes;
    @Column(name = "plan_start_min", nullable = false) private LocalDateTime planStartMin;
    @Column(name = "plan_end_max", nullable = false) private LocalDateTime planEndMax;
    @Column(name = "cover_url", nullable = false, length = 2048) private String coverUrl;
    @Column(name = "cover_object_key", nullable = false, length = 1024) private String coverObjectKey;
    @Column(name = "cover_file_size", nullable = false) private Long coverFileSize;
    @Column(name = "cover_mime_type", nullable = false, length = 100) private String coverMimeType;
    @Column(name = "cover_material_id", length = 36) private String coverMaterialId;
    @Column(name = "host_id", nullable = false) private Long hostId;
    @Column(name = "host_name", nullable = false, length = 50) private String hostName;
    @Column(name = "host_avatar", length = 2048) private String hostAvatar;
    @Column(nullable = false, columnDefinition = "json") private String attachments;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "bound_live_count", nullable = false) private Integer boundLiveCount = 0;
    @Column(name = "submitter_id", nullable = false) private Long submitterId;
    @Column(name = "submitter_name", nullable = false, length = 50) private String submitterName;
    @Column(name = "department_name", length = 200) private String departmentName;
    @Column(name = "submitted_time", nullable = false) private LocalDateTime submittedTime;
    @CreationTimestamp @Column(name = "created_time", nullable = false, updatable = false) private LocalDateTime createdTime;
    @UpdateTimestamp @Column(name = "updated_time", nullable = false) private LocalDateTime updatedTime;
    /** 软删时间；非空则列表/详情视为不存在。 */
    @Column(name = "deleted_time") private LocalDateTime deletedTime;
    /** 删除人 sys_useraccount.rs_mainkey_id。 */
    @Column(name = "deleted_by") private Long deletedBy;
    /** 删除人姓名快照。 */
    @Column(name = "deleted_by_name", length = 50) private String deletedByName;
}
