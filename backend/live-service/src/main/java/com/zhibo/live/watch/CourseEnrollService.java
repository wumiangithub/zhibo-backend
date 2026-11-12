package com.zhibo.live.watch;

import com.zhibo.common.UuidIds;
import com.zhibo.course.CourseEntitlementSupport;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.live.watch.dto.EnrollCourseRequest;
import com.zhibo.live.watch.dto.EnrollCourseResponse;
import com.zhibo.viewer.ViewerRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程分享链进房：upsert {@code course_user}；可选写入员工邀请（first-write-wins）。
 */
@Service
@RequiredArgsConstructor
public class CourseEnrollService {

    private final CourseRepository courseRepository;
    private final CourseUserRepository courseUserRepository;
    private final ViewerRepository viewerRepository;

    @Transactional
    public EnrollCourseResponse enroll(String courseId, String viewerUsername, EnrollCourseRequest request) {
        if (viewerUsername == null || viewerUsername.isBlank()) {
            throw new IllegalArgumentException("uid 无效");
        }
        CourseEntity course = requireCourse(courseId);
        if (!viewerRepository.existsByUsername(viewerUsername)) {
            throw new IllegalArgumentException("观众不存在");
        }

        boolean created = false;
        CourseUserEntity row = courseUserRepository
                .findByCourseIdAndUsername(course.getId(), viewerUsername)
                .orElse(null);
        if (row == null) {
            LocalDateTime now = LocalDateTime.now();
            row = new CourseUserEntity();
            row.setCourseId(course.getId());
            row.setUsername(viewerUsername);
            row.setEntitlementStatus(1);
            row.setAcquireWay("purchase");
            row.setEntitlementExpireTime(CourseEntitlementSupport.expireAtJoin(course, now));
            created = true;
        }

        Long inviterId = request == null ? null : request.getInviterId();
        if (inviterId != null && inviterId > 0 && row.getEmployeeInviterId() == null) {
            row.setEmployeeInviterId(inviterId);
            // 邀请加入时间口径 = created_time（新建时与入表同刻；补邀请人时用已有 created_time）
            LocalDateTime inviteAt = row.getCreatedTime() != null ? row.getCreatedTime() : LocalDateTime.now();
            row.setEmployeeInviteTime(inviteAt);
        }

        courseUserRepository.save(row);

        if (created) {
            course.setTotalUserCount(Math.max(0, course.getTotalUserCount()) + 1);
            courseRepository.save(course);
        }

        return new EnrollCourseResponse(
                row.getId(), row.getUsername(), course.getId(), created, course.getTitle());
    }

    private CourseEntity requireCourse(String courseId) {
        String uuid = UuidIds.require(courseId, "课程 id 无效", "课程 id 须为本站 UUID");
        return courseRepository
                .findById(uuid)
                .filter(c -> c.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
    }
}
