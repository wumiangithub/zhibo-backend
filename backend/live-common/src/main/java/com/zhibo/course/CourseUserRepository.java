package com.zhibo.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUserRepository extends JpaRepository<CourseUserEntity, Long> {

    Optional<CourseUserEntity> findByCourseIdAndUsername(String courseId, String username);

    List<CourseUserEntity> findByCourseIdOrderByCreatedTimeDescIdDesc(String courseId);

    List<CourseUserEntity> findByCourseIdAndUsernameInOrderByCreatedTimeDescIdDesc(
            String courseId, Collection<String> usernames);

    List<CourseUserEntity> findByCourseIdAndEntitlementExpireTimeIsNull(String courseId);

    Optional<CourseUserEntity> findByCourseIdAndUsernameAndEntitlementStatus(
            String courseId, String username, int entitlementStatus);
}
