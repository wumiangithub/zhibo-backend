package com.zhibo.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 课程目录节点 {@code course_catalog_node}。 */
@Entity
@Table(name = "course_catalog_node")
@Getter
@Setter
public class CourseCatalogNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, length = 32)
    private String courseId;

    /** 父节点 id；根下为 0 */
    @Column(name = "parent_id", nullable = false)
    private long parentId;

    /** {@link CourseCatalogNodeType} */
    @Column(name = "node_type", nullable = false)
    private int nodeType;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(name = "cover_material_id", length = 32)
    private String coverMaterialId;

    @Column(name = "cover_url", length = 2048)
    private String coverUrl;

    /** {@link CourseCatalogContentKind}；容器为空 */
    @Column(name = "content_kind")
    private Integer contentKind;

    @Column(name = "content_id", length = 32)
    private String contentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;
}
