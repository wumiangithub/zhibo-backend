package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.live.watch.dto.EnrollCourseRequest;
import com.zhibo.viewer.ViewerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseEnrollServiceTest {

    private static final String CID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseUserRepository courseUserRepository;
    @Mock
    private ViewerRepository viewerRepository;

    private CourseEnrollService service;

    @BeforeEach
    void setUp() {
        service = new CourseEnrollService(courseRepository, courseUserRepository, viewerRepository);
    }

    @Test
    void enroll_createsRowAndWritesInviter() {
        CourseEntity course = new CourseEntity();
        course.setId(CID);
        course.setTitle("演示课");
        course.setTotalUserCount(0);
        when(courseRepository.findById(CID)).thenReturn(Optional.of(course));
        when(viewerRepository.existsByUsername("12")).thenReturn(true);
        when(courseUserRepository.findByCourseIdAndUsername(CID, "12")).thenReturn(Optional.empty());
        when(courseUserRepository.save(any())).thenAnswer(inv -> {
            CourseUserEntity row = inv.getArgument(0);
            row.setId(7L);
            return row;
        });

        EnrollCourseRequest req = new EnrollCourseRequest();
        req.setInviterId(10001L);

        var res = service.enroll(CID, "12", req);
        assertEquals(7L, res.getId());
        assertEquals("12", res.getUsername());
        assertTrue(res.isCreated());
        assertEquals("演示课", res.getTitle());
        assertEquals(1, course.getTotalUserCount());

        ArgumentCaptor<CourseUserEntity> cap = ArgumentCaptor.forClass(CourseUserEntity.class);
        verify(courseUserRepository).save(cap.capture());
        assertEquals(10001L, cap.getValue().getEmployeeInviterId());
        assertEquals("purchase", cap.getValue().getAcquireWay());
        assertNull(cap.getValue().getEntitlementExpireTime());
    }

    @Test
    void enroll_daysSetsExpireFromNow() {
        CourseEntity course = new CourseEntity();
        course.setId(CID);
        course.setTitle("演示课");
        course.setTotalUserCount(0);
        course.setAccessValidityType("days");
        course.setAccessValidityDays(7);
        when(courseRepository.findById(CID)).thenReturn(Optional.of(course));
        when(viewerRepository.existsByUsername("12")).thenReturn(true);
        when(courseUserRepository.findByCourseIdAndUsername(CID, "12")).thenReturn(Optional.empty());
        when(courseUserRepository.save(any())).thenAnswer(inv -> {
            CourseUserEntity row = inv.getArgument(0);
            row.setId(7L);
            return row;
        });

        service.enroll(CID, "12", new EnrollCourseRequest());

        ArgumentCaptor<CourseUserEntity> cap = ArgumentCaptor.forClass(CourseUserEntity.class);
        verify(courseUserRepository).save(cap.capture());
        assertEquals("purchase", cap.getValue().getAcquireWay());
        assertNotNull(cap.getValue().getEntitlementExpireTime());
    }

    @Test
    void enroll_existing_doesNotOverwriteInviter() {
        CourseEntity course = new CourseEntity();
        course.setId(CID);
        course.setTitle("演示课");
        course.setTotalUserCount(3);
        when(courseRepository.findById(CID)).thenReturn(Optional.of(course));
        when(viewerRepository.existsByUsername("12")).thenReturn(true);

        CourseUserEntity existing = new CourseUserEntity();
        existing.setId(7L);
        existing.setCourseId(CID);
        existing.setUsername("12");
        existing.setEmployeeInviterId(90001L);
        when(courseUserRepository.findByCourseIdAndUsername(CID, "12")).thenReturn(Optional.of(existing));
        when(courseUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollCourseRequest req = new EnrollCourseRequest();
        req.setInviterId(10001L);

        var res = service.enroll(CID, "12", req);
        assertFalse(res.isCreated());
        assertEquals(3, course.getTotalUserCount());
        assertEquals(90001L, existing.getEmployeeInviterId());
    }
}
