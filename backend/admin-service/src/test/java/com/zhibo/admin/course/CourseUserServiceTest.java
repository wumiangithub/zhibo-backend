package com.zhibo.admin.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.course.dto.CourseUserListResponse;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserImportRepository;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseUserServiceTest {

    private static final String CID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseUserRepository courseUserRepository;
    @Mock
    private CourseUserImportRepository courseUserImportRepository;
    @Mock
    private CourseCatalogNodeRepository catalogNodeRepository;
    @Mock
    private CourseLessonProgressRepository progressRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private ViewerTagMemberRepository viewerTagMemberRepository;
    @Mock
    private ViewerTagRepository viewerTagRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CourseUserService courseUserService;

    @Test
    void list_marksExpiredWhenDaysAndPast() {
        CourseEntity course = new CourseEntity();
        course.setId(CID);
        course.setAccessValidityType("days");
        course.setAccessValidityDays(7);
        when(courseRepository.findById(CID)).thenReturn(Optional.of(course));

        CourseUserEntity row = new CourseUserEntity();
        row.setId(1L);
        row.setCourseId(CID);
        row.setViewerId(9L);
        row.setEntitlementStatus(1);
        row.setAcquireWay("purchase");
        row.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        row.setEntitlementExpireTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        when(courseUserRepository.findByCourseIdOrderByCreatedTimeDescIdDesc(CID))
                .thenReturn(List.of(row));

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(9L);
        viewer.setNickname("张三");
        viewer.setPhone("13812345678");
        when(viewerRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(viewer));
        when(catalogNodeRepository.countActiveByCourseIdAndNodeType(
                        org.mockito.ArgumentMatchers.eq(CID), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(10L);
        when(progressRepository.findByCourseId(CID)).thenReturn(List.of());
        when(viewerTagMemberRepository.findByViewerIdIn(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CourseUserListResponse res = courseUserService.list(CID, 1, 20, null, null, null);
        assertEquals(1, res.getTotal());
        assertEquals("expired", res.getList().get(0).getEntitlementState());
        assertEquals("已到期", res.getList().get(0).getEntitlementStateLabel());
        assertEquals(0D, res.getList().get(0).getChapterCompletionRate());
    }

    @Test
    void list_longDoesNotExpire() {
        CourseEntity course = new CourseEntity();
        course.setId(CID);
        course.setAccessValidityType("long");
        when(courseRepository.findById(CID)).thenReturn(Optional.of(course));

        CourseUserEntity row = new CourseUserEntity();
        row.setId(1L);
        row.setCourseId(CID);
        row.setViewerId(9L);
        row.setEntitlementStatus(1);
        row.setAcquireWay("import");
        row.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        row.setEntitlementExpireTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        when(courseUserRepository.findByCourseIdOrderByCreatedTimeDescIdDesc(CID))
                .thenReturn(List.of(row));

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(9L);
        viewer.setNickname("李四");
        when(viewerRepository.findAllById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(viewer));
        when(catalogNodeRepository.countActiveByCourseIdAndNodeType(
                        org.mockito.ArgumentMatchers.eq(CID), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(10L);
        when(progressRepository.findByCourseId(CID)).thenReturn(List.of());
        when(viewerTagMemberRepository.findByViewerIdIn(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        CourseUserListResponse res = courseUserService.list(CID, 1, 20, null, null, "valid");
        assertEquals(1, res.getTotal());
        assertEquals("valid", res.getList().get(0).getEntitlementState());
        assertEquals("长期", res.getList().get(0).getExpireDisplay());
        assertEquals("导入", res.getList().get(0).getAcquireWayLabel());
    }

    @Test
    void parseOptionalExpireTime_dateOnlyBecomesStartOfDay() {
        assertEquals(
                LocalDateTime.of(2026, 12, 31, 0, 0, 0),
                CourseUserService.parseOptionalExpireTime("2026-12-31"));
        assertEquals(
                LocalDateTime.of(2026, 12, 31, 23, 59, 59),
                CourseUserService.parseOptionalExpireTime("2026-12-31 23:59:59"));
        Assertions.assertNull(CourseUserService.parseOptionalExpireTime(null));
        Assertions.assertNull(CourseUserService.parseOptionalExpireTime("  "));
    }

    @Test
    void resolveJoinExpire_prefersCustom() {
        CourseEntity course = new CourseEntity();
        course.setAccessValidityType("days");
        course.setAccessValidityDays(7);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime custom = LocalDateTime.of(2026, 6, 1, 0, 0);
        assertEquals(custom, CourseUserService.resolveJoinExpire(course, now, custom));
        assertEquals(
                now.plusDays(7), CourseUserService.resolveJoinExpire(course, now, null));
    }
}
