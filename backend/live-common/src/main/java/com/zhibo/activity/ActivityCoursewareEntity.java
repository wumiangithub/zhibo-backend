package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 活动课件；name/url/type 为绑定时快照。 */
@Entity
@Table(name = "activity_courseware")
@Getter
@Setter
public class ActivityCoursewareEntity {

    @EmbeddedId
    private ActivityCoursewareId id;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    /** image / video / document */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
