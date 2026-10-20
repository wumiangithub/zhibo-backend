package com.zhibo.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseAuditRepository extends JpaRepository<CourseAuditEntity, String> {

    List<CourseAuditEntity> findByCourseIdOrderByCreatedTimeDesc(String courseId);
}
