package com.zhibo.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterialEntity, CourseMaterialId> {

    @Query("SELECT m FROM CourseMaterialEntity m WHERE m.id.courseId IN :ids "
            + "ORDER BY m.id.courseId ASC, m.sortOrder ASC")
    List<CourseMaterialEntity> findByCourseIds(@Param("ids") Collection<String> ids);

    void deleteByIdCourseId(String courseId);
}
