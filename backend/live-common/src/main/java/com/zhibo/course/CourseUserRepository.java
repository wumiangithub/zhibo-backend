package com.zhibo.course;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUserRepository extends JpaRepository<CourseUserEntity, Long> {

    Optional<CourseUserEntity> findByCourseIdAndViewerId(String courseId, Long viewerId);
}
