package com.zhibo.course;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 课程归属人。姓名不落在这张表，列表时按员工表查。 */
@Entity
@Table(name = "course_owner")
@Getter
@Setter
public class CourseOwnerEntity {

    @EmbeddedId
    private CourseOwnerId id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
