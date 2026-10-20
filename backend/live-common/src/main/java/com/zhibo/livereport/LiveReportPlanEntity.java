package com.zhibo.livereport;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 报备计划时间段；与 {@link LiveReportEntity#getPlanTimes()} JSON 同步。 */
@Entity
@Table(name = "live_report_plan")
@Getter
@Setter
public class LiveReportPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, length = 36)
    private String reportId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
