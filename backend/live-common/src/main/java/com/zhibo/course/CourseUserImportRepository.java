package com.zhibo.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUserImportRepository extends JpaRepository<CourseUserImportEntity, Long> {

    List<CourseUserImportEntity> findTop100ByCourseIdOrderByImportTimeDesc(String courseId);
}