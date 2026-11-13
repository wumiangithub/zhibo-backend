package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 订单关联课程，一单多课（见 zhibo-shared/docs/订单落库.md）。 */
@Entity
@Table(name = "activity_order_course")
@Getter
@Setter
public class ActivityOrderCourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 本站课程 id，对齐 course.id，与列表接口 courseId 相同。 */
    @Column(name = "course_id", nullable = false, length = 32)
    private String courseId;
}
