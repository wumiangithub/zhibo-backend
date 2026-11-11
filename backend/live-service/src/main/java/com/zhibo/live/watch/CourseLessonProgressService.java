package com.zhibo.live.watch;

import com.zhibo.common.UuidIds;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseLessonParticipateStatus;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.live.watch.dto.CourseLessonProgressRequest;
import com.zhibo.live.watch.dto.CourseLessonProgressResponse;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 课程小节学习进度上报（D101）。 */
@Service
@RequiredArgsConstructor
public class CourseLessonProgressService {

    private static final Set<String> EVENTS =
            Set.of("open", "heartbeat", "scroll_bottom", "live_ended");

    private final CourseRepository courseRepository;
    private final CourseCatalogNodeRepository catalogNodeRepository;
    private final CourseLessonProgressRepository progressRepository;

    @Transactional
    public CourseLessonProgressResponse report(
            String courseIdRaw, long viewerId, CourseLessonProgressRequest request) {
        if (viewerId <= 0) {
            throw new IllegalArgumentException("uid 无效");
        }
        if (request == null || request.getLessonNodeId() == null || request.getLessonNodeId() <= 0) {
            throw new IllegalArgumentException("lessonNodeId 无效");
        }
        String event = request.getEvent() == null ? "" : request.getEvent().trim().toLowerCase(Locale.ROOT);
        if (!EVENTS.contains(event)) {
            throw new IllegalArgumentException("event 须为 open / heartbeat / scroll_bottom / live_ended");
        }

        String courseId = UuidIds.require(courseIdRaw, "课程 id 无效", "课程 id 须为本站 UUID");
        courseRepository
                .findById(courseId)
                .filter(c -> c.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));

        CourseCatalogNodeEntity lesson =
                catalogNodeRepository
                        .findByIdAndCourseIdAndDeletedTimeIsNull(request.getLessonNodeId(), courseId)
                        .orElseThrow(() -> new IllegalArgumentException("小节不存在"));
        if (lesson.getNodeType() != CourseCatalogNodeType.LESSON) {
            throw new IllegalArgumentException("节点不是小节");
        }
        Integer contentKindObj = lesson.getContentKind();
        if (contentKindObj == null || !CourseCatalogContentKind.isKnown(contentKindObj)) {
            throw new IllegalArgumentException("小节内容类型无效");
        }
        int contentKind = contentKindObj;

        CourseLessonProgressEntity row =
                progressRepository
                        .findByCourseIdAndLessonNodeIdAndUid(
                                courseId, lesson.getId(), viewerId)
                        .orElseGet(() -> newRow(courseId, lesson.getId(), viewerId));

        LocalDateTime now = LocalDateTime.now();
        markParticipated(row, now);

        long delta = request.getDeltaLearnDurationSec() == null ? 0L : request.getDeltaLearnDurationSec();
        if (delta < 0) {
            throw new IllegalArgumentException("deltaLearnDurationSec 不可为负");
        }
        if (delta > 0) {
            row.setLearnDurationSec(safeAdd(row.getLearnDurationSec(), delta));
        }

        if (request.getProgressBarSec() != null) {
            if (request.getProgressBarSec() < 0) {
                throw new IllegalArgumentException("progressBarSec 不可为负");
            }
            Integer cur = row.getProgressBarSec();
            int next = request.getProgressBarSec();
            row.setProgressBarSec(cur == null ? next : Math.max(cur, next));
        }

        if (request.getProgressPercent() != null) {
            int pct = request.getProgressPercent();
            if (pct < 0 || pct > 100) {
                throw new IllegalArgumentException("progressPercent 须为 0～100");
            }
            row.setProgressPercent(Math.max(row.getProgressPercent(), pct));
        }

        boolean shouldComplete = shouldComplete(contentKind, event, row.getProgressPercent());
        if (shouldComplete) {
            markCompleted(row, now);
        }

        row.setLastLearnTime(now);
        progressRepository.save(row);

        boolean completed = row.getParticipateStatus() == CourseLessonParticipateStatus.COMPLETED;
        return new CourseLessonProgressResponse(
                lesson.getId(),
                row.getParticipateStatus(),
                row.getLearnDurationSec(),
                row.getProgressBarSec(),
                row.getProgressPercent(),
                completed);
    }

    private static CourseLessonProgressEntity newRow(String courseId, long lessonNodeId, long viewerId) {
        CourseLessonProgressEntity row = new CourseLessonProgressEntity();
        row.setCourseId(courseId);
        row.setLessonNodeId(lessonNodeId);
        row.setUid(viewerId);
        row.setParticipateStatus(CourseLessonParticipateStatus.NOT_STARTED);
        row.setLearnDurationSec(0L);
        row.setProgressPercent(0);
        return row;
    }

    private static void markParticipated(CourseLessonProgressEntity row, LocalDateTime now) {
        if (row.getFirstParticipateTime() == null) {
            row.setFirstParticipateTime(now);
        }
        if (row.getParticipateStatus() == CourseLessonParticipateStatus.NOT_STARTED) {
            row.setParticipateStatus(CourseLessonParticipateStatus.IN_PROGRESS);
        }
    }

    private static void markCompleted(CourseLessonProgressEntity row, LocalDateTime now) {
        if (row.getParticipateStatus() != CourseLessonParticipateStatus.COMPLETED) {
            row.setParticipateStatus(CourseLessonParticipateStatus.COMPLETED);
            row.setCompletedTime(now);
        }
        row.setProgressPercent(Math.max(row.getProgressPercent(), 100));
    }

    private static boolean shouldComplete(int contentKind, String event, int progressPercent) {
        return switch (contentKind) {
            case CourseCatalogContentKind.IMAGE -> "open".equals(event);
            case CourseCatalogContentKind.DOCUMENT, CourseCatalogContentKind.ARTICLE ->
                    "scroll_bottom".equals(event);
            case CourseCatalogContentKind.LIVE ->
                    "live_ended".equals(event) || progressPercent >= 100;
            case CourseCatalogContentKind.VIDEO -> progressPercent >= 100;
            default -> false;
        };
    }

    private static long safeAdd(long base, long delta) {
        long sum = base + delta;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
