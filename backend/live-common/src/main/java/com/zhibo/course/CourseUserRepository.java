package com.zhibo.course;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUserRepository extends JpaRepository<CourseUserEntity, Long> {

    Optional<CourseUserEntity> findByCourseIdAndUid(String courseId, Long uid);

    List<CourseUserEntity> findByCourseIdOrderByCreatedTimeDescIdDesc(String courseId);

    List<CourseUserEntity> findByCourseIdAndUidInOrderByCreatedTimeDescIdDesc(
            String courseId, Collection<Long> uids);

    List<CourseUserEntity> findByCourseIdAndEntitlementExpireTimeIsNull(String courseId);

    Optional<CourseUserEntity> findByCourseIdAndUidAndEntitlementStatus(
            String courseId, Long uid, int entitlementStatus);
}
