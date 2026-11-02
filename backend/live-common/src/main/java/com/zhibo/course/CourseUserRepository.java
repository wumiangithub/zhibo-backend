package com.zhibo.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUserRepository extends JpaRepository<CourseUserEntity, Long> {

    Optional<CourseUserEntity> findByCourseIdAndViewerId(String courseId, Long viewerId);

    List<CourseUserEntity> findByCourseIdOrderByCreatedTimeDescIdDesc(String courseId);

    List<CourseUserEntity> findByCourseIdAndViewerIdInOrderByCreatedTimeDescIdDesc(
            String courseId, Collection<Long> viewerIds);

    List<CourseUserEntity> findByCourseIdAndEntitlementExpireTimeIsNull(String courseId);
}
