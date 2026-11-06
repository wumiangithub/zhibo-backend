package com.zhibo.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseLessonProgressRepository
        extends JpaRepository<CourseLessonProgressEntity, Long>,
                JpaSpecificationExecutor<CourseLessonProgressEntity> {

    Optional<CourseLessonProgressEntity> findByCourseIdAndLessonNodeIdAndViewerId(
            String courseId, Long lessonNodeId, Long viewerId);

    List<CourseLessonProgressEntity> findByCourseIdAndViewerId(String courseId, Long viewerId);

    List<CourseLessonProgressEntity> findByCourseId(String courseId);

    List<CourseLessonProgressEntity> findByCourseIdAndLessonNodeIdIn(
            String courseId, Collection<Long> lessonNodeIds);

    @Query("SELECT p FROM CourseLessonProgressEntity p WHERE p.courseId = :courseId "
            + "AND p.lessonNodeId = :lessonNodeId AND p.participateStatus >= :minStatus")
    List<CourseLessonProgressEntity> findByCourseLessonAndMinStatus(
            @Param("courseId") String courseId,
            @Param("lessonNodeId") Long lessonNodeId,
            @Param("minStatus") int minStatus);

    @Query("SELECT COUNT(p) FROM CourseLessonProgressEntity p WHERE p.courseId = :courseId "
            + "AND p.viewerId = :viewerId AND p.participateStatus = :status")
    long countByCourseViewerAndStatus(
            @Param("courseId") String courseId,
            @Param("viewerId") Long viewerId,
            @Param("status") int status);
}
