package com.zhibo.admin.course;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.course.dto.CourseAuditRequest;
import com.zhibo.admin.course.dto.CourseAuditResultResponse;
import com.zhibo.admin.course.dto.CourseIdResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.course.dto.CourseShelfRequest;
import com.zhibo.admin.course.dto.CreateCourseRequest;
import com.zhibo.admin.course.dto.OutsideCourseListResponse;
import com.zhibo.course.CourseAuditEntity;
import com.zhibo.course.CourseAuditRepository;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseMaterialRepository;
import com.zhibo.course.CourseOwnerEntity;
import com.zhibo.course.CourseOwnerId;
import com.zhibo.course.CourseOwnerRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final long OPERATOR_ID = 10001L;

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseOwnerRepository ownerRepository;
    @Mock
    private CourseMaterialRepository materialBindingRepository;
    @Mock
    private CourseAuditRepository auditRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    @InjectMocks
    private CourseService courseService;

    @Test
    void create_startsPendingAndWritesSubmit() {
        stubPeople();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseIdResponse response = courseService.create(request(true), OPERATOR_ID);

        ArgumentCaptor<CourseEntity> saved = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseRepository).save(saved.capture());
        assertEquals("新课程", saved.getValue().getTitle());
        assertEquals("pending", saved.getValue().getRiskStatus());
        assertEquals("pending", saved.getValue().getShelfStatus());
        assertEquals(Boolean.TRUE, saved.getValue().getAutoList());
        assertNull(saved.getValue().getListedTime());
        ArgumentCaptor<CourseAuditEntity> audit = ArgumentCaptor.forClass(CourseAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("submit", audit.getValue().getAction());
        assertNull(audit.getValue().getResult());
        assertNotNull(response.getId());
    }

    @Test
    void create_rejectsCoverThatFailedAudit() {
        MaterialEntity cover = cover("cover-1");
        cover.setRiskAuditStatus(0);
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.create(request(true), OPERATOR_ID));
        assertTrue(ex.getMessage().contains("封面"));
    }

    @Test
    void create_rejectsOversizedOrNonImageCover() {
        MaterialEntity huge = cover("cover-1");
        huge.setFileSize(CourseService.COVER_MAX_BYTES + 1);
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(huge));
        assertThrows(IllegalArgumentException.class, () -> courseService.create(request(true), OPERATOR_ID));

        MaterialEntity gif = cover("cover-1");
        gif.setMimeType("image/gif");
        gif.setName("a.gif");
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(gif));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.create(request(true), OPERATOR_ID));
        assertTrue(ex.getMessage().contains("JPG/PNG"));
    }

    @Test
    void create_requiresOwner() {
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        CreateCourseRequest body = request(true);
        body.setOwnerIds(List.of());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.create(body, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("归属人"));
    }

    @Test
    void audit_autoListTurnsShelfOn() {
        CourseEntity entity = stored("pending", "pending", true);
        stubOperator();
        CourseAuditRequest body = new CourseAuditRequest();
        body.setResult("passed");

        CourseAuditResultResponse response = courseService.audit(entity.getId(), body, OPERATOR_ID);

        assertEquals("passed", response.getRiskStatus());
        assertEquals("on", response.getShelfStatus());
        assertNotNull(entity.getListedTime());
    }

    @Test
    void audit_withoutAutoListStaysPending() {
        CourseEntity entity = stored("pending", "pending", false);
        stubOperator();
        CourseAuditRequest body = new CourseAuditRequest();
        body.setResult("passed");

        CourseAuditResultResponse response = courseService.audit(entity.getId(), body, OPERATOR_ID);

        assertEquals("passed", response.getRiskStatus());
        assertEquals("pending", response.getShelfStatus());
        assertNull(entity.getListedTime());
    }

    @Test
    void audit_rejectBansShelf() {
        CourseEntity entity = stored("on", "pending", true);
        entity.setListedTime(java.time.LocalDateTime.of(2026, 10, 1, 12, 0));
        stubOperator();
        CourseAuditRequest body = new CourseAuditRequest();
        body.setResult("rejected");
        body.setReason("资料不全");

        CourseAuditResultResponse response = courseService.audit(entity.getId(), body, OPERATOR_ID);

        assertEquals("rejected", response.getRiskStatus());
        assertEquals("banned", response.getShelfStatus());
        assertNotNull(entity.getListedTime());
    }

    @Test
    void shelf_rejectsWhenRiskPending() {
        CourseEntity entity = stored("pending", "pending", true);
        CourseShelfRequest body = new CourseShelfRequest();
        body.setListed(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.shelf(entity.getId(), body));
        assertTrue(ex.getMessage().contains("风控未通过"));
    }

    @Test
    void outsideList_onlyListedAndPassed_omitsCountFields() throws Exception {
        CourseEntity listed = outsideCourse("on", "passed", null);
        listed.setTitle("已上架课");
        listed.setIntroduction("  ");
        listed.setCoverUrl("https://cdn.example/cover.jpg");
        listed.setListedTime(null);
        listed.setUpdatedTime(LocalDateTime.of(2026, 10, 17, 9, 30, 0));
        listed.setContentCount(3);
        listed.setEntitledUserCount(4);
        listed.setTotalUserCount(5);
        listed.setSalesCount(6);

        CourseEntity pending = outsideCourse("pending", "passed", null);
        CourseEntity rejected = outsideCourse("on", "rejected", null);
        CourseEntity off = outsideCourse("off", "passed", null);
        CourseEntity banned = outsideCourse("banned", "passed", null);
        CourseEntity deleted = outsideCourse("on", "passed", LocalDateTime.of(2026, 10, 1, 0, 0));
        CourseEntity noOwner = outsideCourse("on", "passed", null);
        noOwner.setTitle("无归属人");
        noOwner.setCoverUrl(" ");
        noOwner.setIntroduction(null);
        noOwner.setListedTime(LocalDateTime.of(2026, 10, 16, 8, 0, 0));
        noOwner.setUpdatedTime(LocalDateTime.of(2026, 10, 16, 8, 1, 2));

        Pageable requested = org.springframework.data.domain.PageRequest.of(0, 20);
        when(courseRepository.search(eq("数学"), eq("on"), eq("passed"), any())).thenReturn(
                new PageImpl<>(List.of(listed, pending, rejected, off, banned, deleted, noOwner), requested, 100));

        CourseOwnerEntity owner = new CourseOwnerEntity();
        owner.setId(new CourseOwnerId(listed.getId(), OPERATOR_ID));
        owner.setSortOrder(0);
        when(ownerRepository.findByCourseIds(any())).thenReturn(List.of(owner));
        when(userRepository.findAllById(any())).thenReturn(List.of(operator()));

        OutsideCourseListResponse response = courseService.outsideList(1, 20, " 数学 ");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).search(eq("数学"), eq("on"), eq("passed"), pageable.capture());
        Sort.Order order = pageable.getValue().getSort().getOrderFor("listedTime");
        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());

        assertEquals(1, response.getPage());
        assertEquals(20, response.getPageSize());
        assertEquals(100, response.getTotal());
        assertEquals(2, response.getList().size());
        assertEquals(listed.getId(), response.getList().get(0).getId());
        assertEquals(noOwner.getId(), response.getList().get(1).getId());
        assertEquals(1, response.getList().get(0).getOwners().size());
        assertEquals(OPERATOR_ID, response.getList().get(0).getOwners().get(0).getId());
        assertEquals("张三", response.getList().get(0).getOwners().get(0).getUserName());
        assertTrue(response.getList().get(1).getOwners().isEmpty());

        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(response);
        for (String forbidden : List.of(
                "contentCount", "entitledUserCount", "totalUserCount", "salesCount",
                "riskStatus", "shelfStatus", "autoList", "canList", "canUnlist", "canDelete",
                "detailHtml", "avatar", "department")) {
            assertFalse(json.contains(forbidden), forbidden);
        }
        String first = mapper.writeValueAsString(response.getList().get(0));
        assertFalse(first.contains("\"introduction\""));
        assertFalse(first.contains("\"listedTime\""));
        assertTrue(first.contains("\"coverUrl\":\"https://cdn.example/cover.jpg\""));
        assertTrue(first.contains("\"updatedTime\":\"2026-10-17 09:30:00\""));
        String second = mapper.writeValueAsString(response.getList().get(1));
        assertFalse(second.contains("\"coverUrl\""));
        assertFalse(second.contains("\"introduction\""));
        assertTrue(second.contains("\"owners\":[]"));
        assertTrue(second.contains("\"listedTime\":\"2026-10-16 08:00:00\""));
    }

    @Test
    void delete_requiresZeroContentAndSales() {
        CourseEntity blocked = stored("pending", "pending", true);
        blocked.setSalesCount(1);
        assertThrows(IllegalArgumentException.class, () -> courseService.delete(blocked.getId()));

        CourseEntity ok = stored("pending", "pending", false);
        courseService.delete(ok.getId());
        assertNotNull(ok.getDeletedTime());
    }

    private CreateCourseRequest request(boolean autoList) {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle(" 新课程 ");
        request.setCoverMaterialId("cover-1");
        request.setAutoList(autoList);
        request.setOwnerIds(List.of(OPERATOR_ID));
        return request;
    }

    private static CourseEntity outsideCourse(String shelf, String risk, LocalDateTime deletedTime) {
        CourseEntity entity = new CourseEntity();
        entity.setId(com.zhibo.common.UuidIds.newId());
        entity.setTitle("课程");
        entity.setShelfStatus(shelf);
        entity.setRiskStatus(risk);
        entity.setDeletedTime(deletedTime);
        entity.setCoverUrl("https://cdn.example/x.jpg");
        entity.setUpdatedTime(LocalDateTime.of(2026, 10, 17, 1, 2, 3));
        return entity;
    }

    private CourseEntity stored(String shelf, String risk, boolean autoList) {
        CourseEntity entity = new CourseEntity();
        entity.setId(com.zhibo.common.UuidIds.newId());
        entity.setShelfStatus(shelf);
        entity.setRiskStatus(risk);
        entity.setAutoList(autoList);
        when(courseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        return entity;
    }

    private SysUserAccountEntity operator() {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(OPERATOR_ID);
        user.setUserName("张三");
        user.setRsStatus(1);
        return user;
    }

    private void stubOperator() {
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
    }

    private void stubPeople() {
        SysUserAccountEntity user = operator();
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(user));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
    }

    private static MaterialEntity cover(String id) {
        MaterialEntity material = new MaterialEntity();
        material.setId(id);
        material.setType("image");
        material.setName("cover.jpg");
        material.setUrl("https://cdn.example/cover.jpg");
        material.setMimeType("image/jpeg");
        material.setFileSize(1024L);
        material.setAiAuditStatus(1);
        material.setRiskAuditStatus(1);
        return material;
    }
}
