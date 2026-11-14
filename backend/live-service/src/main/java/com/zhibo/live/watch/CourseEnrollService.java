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
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程分享进房：upsert {@code course_user}；可选写入员工邀请（first-write-wins）。
 * 邀请字段与直播 attribute 同形：{@code inviterKind} / {@code inviterId} / 观众邀请人 {@code username}；
 * audience 本轮不落库（D77 口径）。
 */
@Service
@RequiredArgsConstructor
public class CourseEnrollService {

    private static final String KIND_EMPLOYEE = "employee";
    private static final String KIND_AUDIENCE = "audience";

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

        applyInvite(row, request);

        courseUserRepository.save(row);

        if (created) {
            course.setTotalUserCount(Math.max(0, course.getTotalUserCount()) + 1);
            courseRepository.save(course);
        }

        return new EnrollCourseResponse(
                row.getId(), row.getUsername(), course.getId(), created, course.getTitle());
    }

    /**
     * 无邀请字段 → 只 upsert。有任邀请字段 → {@code inviterId} 必传；
     * employee 写员工邀请人；audience 校验邀请人 username 后拒收。
     */
    private static void applyInvite(CourseUserEntity row, EnrollCourseRequest request) {
        if (request == null || !hasInviteIntent(request)) {
            return;
        }
        Long inviterId = request.getInviterId();
        if (inviterId == null || inviterId <= 0) {
            throw new IllegalArgumentException("inviterId 无效");
        }
        String kind = normalizeKind(request.getInviterKind());
        if (KIND_AUDIENCE.equals(kind)) {
            String inviterUsername = request.getUsername() == null ? "" : request.getUsername().trim();
            if (inviterUsername.isEmpty()) {
                throw new IllegalArgumentException("观众邀请须传邀请人 username");
            }
            throw new IllegalArgumentException("不记录观众邀请");
        }
        if (!KIND_EMPLOYEE.equals(kind)) {
            throw new IllegalArgumentException("inviterKind 须为 employee 或 audience");
        }
        if (row.getEmployeeInviterId() == null) {
            row.setEmployeeInviterId(inviterId);
            // 邀请加入时间口径 = created_time（新建时与入表同刻；补邀请人时用已有 created_time）
            LocalDateTime inviteAt =
                    row.getCreatedTime() != null ? row.getCreatedTime() : LocalDateTime.now();
            row.setEmployeeInviteTime(inviteAt);
        }
    }

    private static boolean hasInviteIntent(EnrollCourseRequest request) {
        if (request.getInviterId() != null) {
            return true;
        }
        if (request.getInviterKind() != null && !request.getInviterKind().isBlank()) {
            return true;
        }
        return request.getUsername() != null && !request.getUsername().isBlank();
    }

    static String normalizeKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return KIND_EMPLOYEE;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private CourseEntity requireCourse(String courseId) {
        String uuid = UuidIds.require(courseId, "课程 id 无效", "课程 id 须为本站 UUID");
        return courseRepository
                .findById(uuid)
                .filter(c -> c.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
    }
}
