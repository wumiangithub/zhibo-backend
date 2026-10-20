package com.zhibo.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CourseOwnerRepository extends JpaRepository<CourseOwnerEntity, CourseOwnerId> {

    @Query("SELECT o FROM CourseOwnerEntity o WHERE o.id.courseId IN :ids "
            + "ORDER BY o.id.courseId ASC, o.sortOrder ASC")
    List<CourseOwnerEntity> findByCourseIds(@Param("ids") Collection<String> ids);

    @Query("SELECT DISTINCT o.id.courseId FROM CourseOwnerEntity o WHERE o.id.userId IN :userIds")
    List<String> findCourseIdsByUserIdIn(@Param("userIds") Collection<Long> userIds);

    void deleteByIdCourseId(String courseId);
}
