package com.zhibo.livereport;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "live_report_audit") @Getter @Setter
public class LiveReportAuditEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "report_id", nullable = false, length = 36) private String reportId;
    @Column(nullable = false, length = 16) private String action;
    @Column(length = 16) private String result;
    @Column(length = 500) private String reason;
    @Column(name = "operator_id", nullable = false) private Long operatorId;
    @Column(name = "operator_name", nullable = false, length = 50) private String operatorName;
    @CreationTimestamp @Column(name = "created_time", nullable = false, updatable = false) private LocalDateTime createdTime;
    @PrePersist void assignId() { if (id == null) id = UUID.randomUUID().toString(); }
}
