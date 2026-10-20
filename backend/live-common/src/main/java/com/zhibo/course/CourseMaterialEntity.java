package com.zhibo.course;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 课程课件；名称、地址、类型、大小为绑定时快照。 */
@Entity
@Table(name = "course_material")
@Getter
@Setter
public class CourseMaterialEntity {

    @EmbeddedId
    private CourseMaterialId id;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    /** image / video / document */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
