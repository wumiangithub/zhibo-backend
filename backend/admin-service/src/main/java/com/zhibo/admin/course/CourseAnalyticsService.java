package com.zhibo.admin.course;

import com.zhibo.admin.course.dto.CourseAnalyticsLessonItem;
import com.zhibo.admin.course.dto.CourseAnalyticsLessonListResponse;
import com.zhibo.admin.course.dto.CourseAnalyticsUserItem;
import com.zhibo.admin.course.dto.CourseAnalyticsUserLessonItem;
import com.zhibo.admin.course.dto.CourseAnalyticsUserLessonListResponse;
import com.zhibo.admin.course.dto.CourseAnalyticsUserListResponse;
import com.zhibo.admin.viewer.ViewerAdminService;
import com.zhibo.common.UuidIds;
import com.zhibo.course.CatalogSiblingOrder;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseEntitlementSupport;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseLessonParticipateStatus;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 课程数据分析：小节概况 / 用户详情 / 进度详情（D101）。 */
@Service
@RequiredArgsConstructor
public class CourseAnalyticsService {

    private static final Set<String> LESSON_SORT =
            Set.of("participateCount", "participateRate", "completeCount", "completeRate");
    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final CourseRepository courseRepository;
    private final CourseCatalogNodeRepository catalogNodeRepository;
    private final CourseUserRepository courseUserRepository;
    private final CourseLessonProgressRepository progressRepository;
    private final ViewerRepository viewerRepository;

    @Transactional(readOnly = true)
    public CourseAnalyticsLessonListResponse listLessons(
            String courseIdRaw, int page, int pageSize, String sortBy, String sortOrder) {
        CourseEntity course = requireCourse(courseIdRaw);
        List<CourseAnalyticsLessonItem> all = buildLessonItems(course);
        String by = sortBy == null ? "" : sortBy.trim();
        if (!by.isEmpty()) {
            if (!LESSON_SORT.contains(by)) {
                throw new IllegalArgumentException(
                        "sortBy 须为 participateCount / participateRate / completeCount / completeRate");
            }
            boolean asc = "asc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
            Comparator<CourseAnalyticsLessonItem> cmp = lessonComparator(by);
            if (!asc) {
                cmp = cmp.reversed();
            }
            all.sort(cmp.thenComparingLong(CourseAnalyticsLessonItem::getLessonNodeId));
        }

        int p = Math.max(page, 1);
        int size = pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        long total = all.size();
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());

        CourseAnalyticsLessonListResponse resp = new CourseAnalyticsLessonListResponse();
        resp.setTotal(total);
        resp.setPage(p);
        resp.setPageSize(size);
        resp.setValidStudentCount(countValidStudents(course));
        resp.setList(new ArrayList<>(all.subList(from, to)));
        return resp;
    }

    @Transactional(readOnly = true)
    public byte[] exportLessonsCsv(String courseIdRaw, String sortBy, String sortOrder) {
        CourseEntity course = requireCourse(courseIdRaw);
        List<CourseAnalyticsLessonItem> all = buildLessonItems(course);
        String by = sortBy == null ? "" : sortBy.trim();
        if (!by.isEmpty()) {
            if (!LESSON_SORT.contains(by)) {
                throw new IllegalArgumentException(
                        "sortBy 须为 participateCount / participateRate / completeCount / completeRate");
            }
            boolean asc = "asc".equalsIgnoreCase(sortOrder == null ? "" : sortOrder.trim());
            Comparator<CourseAnalyticsLessonItem> cmp = lessonComparator(by);
            if (!asc) {
                cmp = cmp.reversed();
            }
            all.sort(cmp.thenComparingLong(CourseAnalyticsLessonItem::getLessonNodeId));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("小节名称,参与人数,参与率,完成人数,完成率\n");
        for (CourseAnalyticsLessonItem item : all) {
            sb.append(csv(item.getTitle())).append(',')
                    .append(item.getParticipateCount()).append(',')
                    .append(formatRate(item.getParticipateRate())).append(',')
                    .append(item.getCompleteCount()).append(',')
                    .append(formatRate(item.getCompleteRate())).append('\n');
        }
        return withBom(sb.toString());
    }

    @Transactional(readOnly = true)
    public CourseAnalyticsUserListResponse listUsers(
            String courseIdRaw, int page, int pageSize, String keywordType, String keyword) {
        CourseEntity course = requireCourse(courseIdRaw);
        List<CourseAnalyticsUserItem> all = buildUserItems(course, keywordType, keyword);
        int p = Math.max(page, 1);
        int size = pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        long total = all.size();
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        CourseAnalyticsUserListResponse resp = new CourseAnalyticsUserListResponse();
        resp.setTotal(total);
        resp.setPage(p);
        resp.setPageSize(size);
        resp.setList(new ArrayList<>(all.subList(from, to)));
        return resp;
    }

    @Transactional(readOnly = true)
    public byte[] exportUsersCsv(
            String courseIdRaw, String keywordType, String keyword) {
        CourseEntity course = requireCourse(courseIdRaw);
        List<CourseAnalyticsUserItem> all = buildUserItems(course, keywordType, keyword);
        StringBuilder sb = new StringBuilder();
        sb.append("用户,用户ID,课程参与状态,小节完成数,小节完成率,累计学习时长,课程权益状态\n");
        for (CourseAnalyticsUserItem item : all) {
            sb.append(csv(item.getNickname())).append(',')
                    .append(item.getUsername()).append(',')
                    .append(csv(participateLabel(item.getParticipateStatus()))).append(',')
                    .append(item.getCompletedLessonCount()).append(',')
                    .append(formatRate(item.getLessonCompletionRate())).append(',')
                    .append(csv(formatDuration(item.getLearnDurationSec()))).append(',')
                    .append(csv(CourseEntitlementSupport.stateLabel(item.getEntitlementDisplay())))
                    .append('\n');
        }
        return withBom(sb.toString());
    }

    @Transactional(readOnly = true)
    public CourseAnalyticsUserLessonListResponse listUserLessons(
            String courseIdRaw, String viewerUsername, int page, int pageSize) {
        if (viewerUsername == null || viewerUsername.isBlank()) {
            throw new IllegalArgumentException("uid 无效");
        }
        CourseEntity course = requireCourse(courseIdRaw);
        CourseUserEntity membership = courseUserRepository
                .findByCourseIdAndUsername(course.getId(), viewerUsername)
                .orElseThrow(() -> new IllegalArgumentException("学员不在本课"));

        List<CourseCatalogNodeEntity> lessons = lessonsInCatalogOrder(course.getId());
        long totalLessonCount = lessons.size();
        Map<Long, CourseLessonProgressEntity> byLesson = progressRepository
                .findByCourseIdAndUsername(course.getId(), viewerUsername)
                .stream()
                .collect(Collectors.toMap(
                        CourseLessonProgressEntity::getLessonNodeId, p -> p, (a, b) -> a));

        long completed = 0;
        List<CourseAnalyticsUserLessonItem> all = new ArrayList<>(lessons.size());
        for (CourseCatalogNodeEntity lesson : lessons) {
            CourseLessonProgressEntity prog = byLesson.get(lesson.getId());
            CourseAnalyticsUserLessonItem item = new CourseAnalyticsUserLessonItem();
            item.setLessonNodeId(lesson.getId());
            item.setTitle(lesson.getTitle());
            item.setContentKind(lesson.getContentKind());
            if (prog == null) {
                item.setParticipateStatus(CourseLessonParticipateStatus.NOT_STARTED);
                item.setLearnDurationSec(0L);
                item.setProgressBarSec(null);
            } else {
                item.setParticipateStatus(prog.getParticipateStatus());
                item.setLearnDurationSec(prog.getLearnDurationSec());
                Integer kind = lesson.getContentKind();
                if (kind != null
                        && (kind == CourseCatalogContentKind.LIVE || kind == CourseCatalogContentKind.VIDEO)) {
                    item.setProgressBarSec(prog.getProgressBarSec());
                } else {
                    item.setProgressBarSec(null);
                }
                if (prog.getParticipateStatus() == CourseLessonParticipateStatus.COMPLETED) {
                    completed++;
                }
            }
            all.add(item);
        }

        int p = Math.max(page, 1);
        int size = pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());

        ViewerEntity viewer = viewerRepository.findByUsername(membership.getUsername()).orElse(null);
        CourseAnalyticsUserLessonListResponse resp = new CourseAnalyticsUserLessonListResponse();
        resp.setUsername(viewerUsername);
        resp.setNickname(
                viewer == null
                        ? "用户_" + viewerUsername
                        : ViewerAdminService.displayNickname(viewer));
        resp.setAvatarUrl(viewer == null ? null : viewer.getAvatarUrl());
        resp.setCompletedLessonCount(completed);
        resp.setLessonCompletionRate(rate(completed, totalLessonCount));
        resp.setTotalLessonCount(totalLessonCount);
        resp.setTotal(all.size());
        resp.setPage(p);
        resp.setPageSize(size);
        resp.setList(new ArrayList<>(all.subList(from, to)));
        return resp;
    }

    private List<CourseAnalyticsUserItem> buildUserItems(
            CourseEntity course, String keywordType, String keyword) {
        String type = keywordType == null || keywordType.isBlank() ? "nickname" : keywordType.trim();
        if (!"nickname".equals(type)) {
            throw new IllegalArgumentException("keywordType 仅支持 nickname");
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<CourseUserEntity> rows =
                courseUserRepository.findByCourseIdOrderByCreatedTimeDescIdDesc(course.getId());
        Set<String> viewerUsernames = rows.stream().map(CourseUserEntity::getUsername).collect(Collectors.toSet());
        Map<String, ViewerEntity> viewers = viewerUsernames.isEmpty()
                ? Map.of()
                : viewerRepository.findByUsernameIn(viewerUsernames).stream()
                        .collect(Collectors.toMap(ViewerEntity::getUsername, v -> v, (a, b) -> a));

        List<CourseCatalogNodeEntity> lessons = lessonsInCatalogOrder(course.getId());
        long totalLessonCount = lessons.size();
        Set<Long> lessonIdSet =
                lessons.stream().map(CourseCatalogNodeEntity::getId).collect(Collectors.toSet());

        Map<String, long[]> progressAgg = new HashMap<>();
        // [0]=completedCount, [1]=learnDurationSec
        for (CourseLessonProgressEntity p : progressRepository.findByCourseId(course.getId())) {
            if (!lessonIdSet.contains(p.getLessonNodeId())) {
                continue;
            }
            long[] arr = progressAgg.computeIfAbsent(p.getUsername(), k -> new long[2]);
            arr[1] += Math.max(0L, p.getLearnDurationSec());
            if (p.getParticipateStatus() == CourseLessonParticipateStatus.COMPLETED) {
                arr[0]++;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String accessType = course.getAccessValidityType();
        List<CourseAnalyticsUserItem> items = new ArrayList<>();
        for (CourseUserEntity row : rows) {
            ViewerEntity viewer = viewers.get(row.getUsername());
            String nickname =
                    viewer == null
                            ? "用户_" + row.getUsername()
                            : ViewerAdminService.displayNickname(viewer);
            if (!kw.isEmpty() && !nickname.toLowerCase(Locale.ROOT).contains(kw)) {
                continue;
            }
            long[] agg = progressAgg.getOrDefault(row.getUsername(), new long[2]);
            long completed = agg[0];
            CourseAnalyticsUserItem item = new CourseAnalyticsUserItem();
            item.setUsername(row.getUsername());
            item.setNickname(nickname);
            item.setAvatarUrl(viewer == null ? null : viewer.getAvatarUrl());
            item.setCompletedLessonCount(completed);
            item.setLessonCompletionRate(rate(completed, totalLessonCount));
            item.setLearnDurationSec(agg[1]);
            item.setParticipateStatus(courseParticipateStatus(completed, totalLessonCount));
            item.setEntitlementDisplay(CourseEntitlementSupport.resolveState(
                    row.getEntitlementStatus(),
                    accessType,
                    row.getEntitlementExpireTime(),
                    now));
            items.add(item);
        }
        return items;
    }

    private static int courseParticipateStatus(long completed, long totalLessons) {
        if (completed <= 0) {
            return CourseLessonParticipateStatus.NOT_STARTED;
        }
        if (totalLessons > 0 && completed >= totalLessons) {
            return CourseLessonParticipateStatus.COMPLETED;
        }
        return CourseLessonParticipateStatus.IN_PROGRESS;
    }

    private static String participateLabel(int status) {
        if (status == CourseLessonParticipateStatus.COMPLETED) {
            return "已完成";
        }
        if (status == CourseLessonParticipateStatus.IN_PROGRESS) {
            return "进行中";
        }
        return "未开始";
    }

    private static String formatDuration(long secRaw) {
        long sec = Math.max(0L, secRaw);
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return h + "小时" + m + "分" + s + "秒";
        }
        if (m > 0) {
            return m + "分" + s + "秒";
        }
        return s + "秒";
    }

    private List<CourseAnalyticsLessonItem> buildLessonItems(CourseEntity course) {
        List<CourseCatalogNodeEntity> lessons = lessonsInCatalogOrder(course.getId());
        Set<String> validViewerUsernames = validViewerUsernameSet(course);
        long validCount = validViewerUsernames.size();

        Map<Long, long[]> stats = new HashMap<>();
        // [0]=participate, [1]=complete among valid
        for (CourseLessonProgressEntity p : progressRepository.findByCourseId(course.getId())) {
            if (!validViewerUsernames.contains(p.getUsername())) {
                continue;
            }
            if (p.getParticipateStatus() < CourseLessonParticipateStatus.IN_PROGRESS) {
                continue;
            }
            long[] arr = stats.computeIfAbsent(p.getLessonNodeId(), k -> new long[2]);
            arr[0]++;
            if (p.getParticipateStatus() == CourseLessonParticipateStatus.COMPLETED) {
                arr[1]++;
            }
        }

        List<CourseAnalyticsLessonItem> items = new ArrayList<>(lessons.size());
        for (CourseCatalogNodeEntity lesson : lessons) {
            long[] arr = stats.getOrDefault(lesson.getId(), new long[2]);
            CourseAnalyticsLessonItem item = new CourseAnalyticsLessonItem();
            item.setLessonNodeId(lesson.getId());
            item.setTitle(lesson.getTitle());
            item.setContentKind(lesson.getContentKind());
            item.setParticipateCount(arr[0]);
            item.setCompleteCount(arr[1]);
            item.setParticipateRate(rate(arr[0], validCount));
            item.setCompleteRate(rate(arr[1], validCount));
            items.add(item);
        }
        return items;
    }

    private long countValidStudents(CourseEntity course) {
        return validViewerUsernameSet(course).size();
    }

    private Set<String> validViewerUsernameSet(CourseEntity course) {
        LocalDateTime now = LocalDateTime.now();
        String accessType = course.getAccessValidityType();
        Set<String> ids = new HashSet<>();
        for (CourseUserEntity row :
                courseUserRepository.findByCourseIdOrderByCreatedTimeDescIdDesc(course.getId())) {
            String state = CourseEntitlementSupport.resolveState(
                    row.getEntitlementStatus(),
                    accessType,
                    row.getEntitlementExpireTime(),
                    now);
            if ("valid".equals(state)) {
                String username = row.getUsername();
                if (username != null && !username.isBlank()) {
                    ids.add(username.trim());
                }
            }
        }
        return ids;
    }

    /** 目录深度优先：同父按 CatalogSiblingOrder，收集全部小节。 */
    private List<CourseCatalogNodeEntity> lessonsInCatalogOrder(String courseId) {
        List<CourseCatalogNodeEntity> all = catalogNodeRepository.findActiveByCourseId(courseId);
        Map<Long, List<CourseCatalogNodeEntity>> byParent = new LinkedHashMap<>();
        for (CourseCatalogNodeEntity n : all) {
            byParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        List<CourseCatalogNodeEntity> out = new ArrayList<>();
        walkLessons(0L, byParent, out);
        return out;
    }

    private static void walkLessons(
            long parentId,
            Map<Long, List<CourseCatalogNodeEntity>> byParent,
            List<CourseCatalogNodeEntity> out) {
        List<CourseCatalogNodeEntity> kids =
                new ArrayList<>(byParent.getOrDefault(parentId, List.of()));
        CatalogSiblingOrder.sortSiblings(kids);
        for (CourseCatalogNodeEntity n : kids) {
            if (n.getNodeType() == CourseCatalogNodeType.LESSON) {
                out.add(n);
            } else if (n.getId() != null) {
                walkLessons(n.getId(), byParent, out);
            }
        }
    }

    private static Comparator<CourseAnalyticsLessonItem> lessonComparator(String sortBy) {
        return switch (sortBy) {
            case "participateCount" ->
                    Comparator.comparingLong(CourseAnalyticsLessonItem::getParticipateCount);
            case "participateRate" ->
                    Comparator.comparingDouble(CourseAnalyticsLessonItem::getParticipateRate);
            case "completeCount" ->
                    Comparator.comparingLong(CourseAnalyticsLessonItem::getCompleteCount);
            case "completeRate" ->
                    Comparator.comparingDouble(CourseAnalyticsLessonItem::getCompleteRate);
            default -> Comparator.comparingLong(CourseAnalyticsLessonItem::getLessonNodeId);
        };
    }

    private static double rate(long num, long den) {
        if (den <= 0 || num <= 0) {
            return 0D;
        }
        return Math.round(num * 1000.0 / den) / 10.0;
    }

    private static String formatRate(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate);
    }

    private static String csv(String raw) {
        String s = raw == null ? "" : raw;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static byte[] withBom(String body) {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + data.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(data, 0, out, UTF8_BOM.length, data.length);
        return out;
    }

    private CourseEntity requireCourse(String courseIdRaw) {
        String uuid = UuidIds.require(courseIdRaw, "课程 id 无效", "课程 id 须为本站 UUID");
        return courseRepository
                .findById(uuid)
                .filter(c -> c.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
    }
}
