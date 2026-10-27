package com.zhibo.viewer;

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

/** 观众标签 {@code viewer_tag}。 */
@Entity
@Table(name = "viewer_tag")
@Getter
@Setter
public class ViewerTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    /** {@link ViewerTagType#MANUAL} / {@link ViewerTagType#AUTO}。 */
    @Column(nullable = false)
    private int type;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "definition_summary", length = 512)
    private String definitionSummary;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_by_name", length = 50)
    private String deletedByName;
}
