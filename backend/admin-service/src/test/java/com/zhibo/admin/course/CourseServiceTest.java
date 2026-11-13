package com.zhibo.admin.course;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.SellPasswordCryptoService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.org.EmployeeDeptResolver;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.course.dto.CourseAuditRequest;
import com.zhibo.admin.course.dto.CourseAuditResultResponse;
import com.zhibo.admin.course.dto.CourseIdResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.course.dto.CourseSalesUpdateRequest;
import com.zhibo.admin.course.dto.CourseShelfRequest;
import com.zhibo.admin.course.dto.CreateCourseRequest;
import com.zhibo.admin.course.dto.OutsideCourseListRequest;
import com.zhibo.admin.course.dto.OutsideCourseListResponse;
import com.zhibo.admin.course.dto.UpdateCourseRequest;
import com.zhibo.course.CourseAuditEntity;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseAuditRepository;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseMaterialRepository;
import com.zhibo.course.CourseOwnerEntity;
import com.zhibo.course.CourseOwnerId;
import com.zhibo.course.CourseOwnerRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserRepository;
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
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock
    private SellPasswordCryptoService sellPasswordCrypto;
    @Mock
    private CourseUserRepository courseUserRepository;
    @Mock
    private EmployeeDeptResolver employeeDeptResolver;
    @Mock
    private CourseCatalogNodeRepository catalogNodeRepository;
    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private CourseService courseService;

    @Test
    void create_passedPendingWhenNotListed() {
        stubPeople();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseIdResponse response = courseService.create(request(false), OPERATOR_ID);

        ArgumentCaptor<CourseEntity> saved = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseRepository).save(saved.capture());
        assertEquals("新课程", saved.getValue().getTitle());
        assertEquals("passed", saved.getValue().getRiskStatus());
        assertEquals("pending", saved.getValue().getShelfStatus());
        assertEquals(Boolean.FALSE, saved.getValue().getAutoList());
        assertNull(saved.getValue().getListedTime());
        assertEquals(Boolean.TRUE, saved.getValue().getSellAlone());
        assertEquals("free", saved.getValue().getSellType());
        assertEquals("长期有效", saved.getValue().getValidityLabel());
        assertEquals("long", saved.getValue().getAccessValidityType());
        assertNull(saved.getValue().getAccessValidityDays());
        assertNull(saved.getValue().getSellPasswordCipher());
        verify(auditRepository, never()).save(any());
        assertNotNull(response.getId());
    }

    @Test
    void create_listedTrueSetsShelfOn() {
        stubPeople();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        courseService.create(request(true), OPERATOR_ID);

        ArgumentCaptor<CourseEntity> saved = ArgumentCaptor.forClass(CourseEntity.class);
        verify(courseRepository).save(saved.capture());
        assertEquals("passed", saved.getValue().getRiskStatus());
        assertEquals("on", saved.getValue().getShelfStatus());
        assertNotNull(saved.getValue().getListedTime());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void updateSales_paidRejected() {
        CourseSalesUpdateRequest paid = new CourseSalesUpdateRequest();
        paid.setSellAlone(true);
        paid.setSellType("paid");
        paid.setAccessValidityType("long");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.updateSales("course-1", paid));
        assertEquals("sellType 无效", ex.getMessage());
    }

    @Test
    void updateSales_encryptedDaysAfterList() {
        CourseEntity entity = stored("pending", "pending", false);
        when(courseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sellPasswordCrypto.encrypt("123456")).thenReturn("cipher");
        when(courseUserRepository.findByCourseIdAndEntitlementExpireTimeIsNull(entity.getId()))
                .thenReturn(List.of());

        CourseSalesUpdateRequest bad = new CourseSalesUpdateRequest();
        bad.setSellAlone(true);
        bad.setSellType("encrypted");
        bad.setPassword("12ab56");
        bad.setAccessValidityType("days");
        bad.setAccessValidityDays(7);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> courseService.updateSales(entity.getId(), bad));
        assertEquals("密码须为恰好6位数字", ex.getMessage());

        CourseSalesUpdateRequest ok = new CourseSalesUpdateRequest();
        ok.setSellAlone(true);
        ok.setSellType("encrypted");
        ok.setPassword("123456");
        ok.setAccessValidityType("days");
        ok.setAccessValidityDays(7);
        var response = courseService.updateSales(entity.getId(), ok);
        assertEquals("cipher", entity.getSellPasswordCipher());
        assertEquals("days", entity.getAccessValidityType());
        assertEquals(7, entity.getAccessValidityDays());
        assertEquals("7天有效", entity.getValidityLabel());
        assertEquals("encrypted", response.getSellType());
        assertEquals("7天有效", response.getValidityLabel());
        verify(sellPasswordCrypto).encrypt("123456");
    }

    @Test
    void updateSales_longToDays_backfillsEmptyExpireOnly() {
        CourseEntity entity = stored("pending", "pending", false);
        entity.setAccessValidityType("long");
        when(courseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseUserEntity emptyExpire = new CourseUserEntity();
        emptyExpire.setId(1L);
        emptyExpire.setCourseId(entity.getId());
        emptyExpire.setCreatedTime(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        when(courseUserRepository.findByCourseIdAndEntitlementExpireTimeIsNull(entity.getId()))
                .thenReturn(List.of(emptyExpire));

        CourseSalesUpdateRequest req = new CourseSalesUpdateRequest();
        req.setSellAlone(true);
        req.setSellType("specified");
        req.setAccessValidityType("days");
        req.setAccessValidityDays(7);
        courseService.updateSales(entity.getId(), req);

        assertEquals(LocalDateTime.of(2026, 1, 8, 10, 0, 0), emptyExpire.getEntitlementExpireTime());
        verify(courseUserRepository).saveAll(any());
    }

    @Test
    void updateSales_daysToDays_doesNotBackfill() {
        CourseEntity entity = stored("pending", "pending", false);
        entity.setAccessValidityType("days");
        entity.setAccessValidityDays(7);
        when(courseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseSalesUpdateRequest req = new CourseSalesUpdateRequest();
        req.setSellAlone(true);
        req.setSellType("specified");
        req.setAccessValidityType("days");
        req.setAccessValidityDays(14);
        courseService.updateSales(entity.getId(), req);

        assertEquals(14, entity.getAccessValidityDays());
        verify(courseUserRepository, org.mockito.Mockito.never())
                .findByCourseIdAndEntitlementExpireTimeIsNull(any());
    }

    @Test
    void updateSales_fixed_backfillsEmptyExpireOnly() {
        CourseEntity entity = stored("pending", "pending", false);
        entity.setAccessValidityType("long");
        when(courseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseUserEntity emptyExpire = new CourseUserEntity();
        emptyExpire.setId(1L);
        emptyExpire.setCourseId(entity.getId());
        emptyExpire.setCreatedTime(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        when(courseUserRepository.findByCourseIdAndEntitlementExpireTimeIsNull(entity.getId()))
                .thenReturn(List.of(emptyExpire));

        CourseSalesUpdateRequest req = new CourseSalesUpdateRequest();
        req.setSellAlone(true);
        req.setSellType("specified");
        req.setAccessValidityType("fixed");
        req.setAccessValidityFixedTime("2026-11-14 00:00:00");
        var response = courseService.updateSales(entity.getId(), req);

        assertEquals("fixed", entity.getAccessValidityType());
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0, 0), entity.getAccessValidityFixedTime());
        assertNull(entity.getAccessValidityDays());
        assertEquals("2026-11-14 00:00:00到期", response.getValidityLabel());
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0, 0), emptyExpire.getEntitlementExpireTime());
        verify(courseUserRepository).saveAll(any());
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
    void audit_passLeavesShelfPending_ignoresAutoList() {
        CourseEntity entity = stored("pending", "pending", true);
        stubOperator();
        CourseAuditRequest body = new CourseAuditRequest();
        body.setResult("passed");

        CourseAuditResultResponse response = courseService.audit(entity.getId(), body, OPERATOR_ID);

        assertEquals("passed", response.getRiskStatus());
        assertEquals("pending", response.getShelfStatus());
        assertNull(entity.getListedTime());
    }

    @Test
    void audit_passUnbansToPending() {
        CourseEntity entity = stored("banned", "rejected", false);
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
        ArgumentCaptor<CourseAuditEntity> audit = ArgumentCaptor.forClass(CourseAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("audit", audit.getValue().getAction());
        assertEquals("rejected", audit.getValue().getResult());
        assertEquals("资料不全", audit.getValue().getReason());
    }

    @Test
    void audit_passStoresOptionalReason() {
        CourseEntity entity = stored("pending", "pending", false);
        stubOperator();
        CourseAuditRequest body = new CourseAuditRequest();
        body.setResult("passed");
        body.setReason(" 资料齐全 ");

        CourseAuditResultResponse response = courseService.audit(entity.getId(), body, OPERATOR_ID);

        assertEquals("passed", response.getRiskStatus());
        assertEquals("pending", response.getShelfStatus());
        ArgumentCaptor<CourseAuditEntity> audit = ArgumentCaptor.forClass(CourseAuditEntity.class);
        verify(auditRepository).save(audit.capture());
        assertEquals("audit", audit.getValue().getAction());
        assertEquals("passed", audit.getValue().getResult());
        assertEquals("资料齐全", audit.getValue().getReason());
    }

    @Test
    void update_pendingCanEditKeepsPassed() {
        CourseEntity entity = stored("pending", "pending", true);
        entity.setTitle("旧标题");
        entity.setCoverMaterialId("cover-old");
        entity.setCoverUrl("https://cdn.example/old.jpg");
        stubPeople();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseIdResponse response = courseService.update(entity.getId(), updateRequest(false), OPERATOR_ID);

        assertEquals(entity.getId(), response.getId());
        assertEquals("passed", entity.getRiskStatus());
        assertEquals("pending", entity.getShelfStatus());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void update_keepsPassedAndUnlists() {
        CourseEntity entity = stored("on", "passed", true);
        entity.setTitle("旧标题");
        entity.setCoverMaterialId("cover-old");
        entity.setCoverUrl("https://cdn.example/old.jpg");
        stubPeople();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CourseIdResponse response = courseService.update(entity.getId(), updateRequest(false), OPERATOR_ID);

        assertEquals(entity.getId(), response.getId());
        assertEquals("新课程", entity.getTitle());
        assertEquals("passed", entity.getRiskStatus());
        assertEquals("off", entity.getShelfStatus());
        assertEquals(Boolean.FALSE, entity.getAutoList());
        assertEquals("cover-1", entity.getCoverMaterialId());
        verify(auditRepository, never()).save(any());
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
    void outsideList_default_returnsEnabledAndOff_withPublishedFields() throws Exception {
        CourseEntity listed = outsideCourse("on", "passed", null);
        listed.setTitle("已上架课");
        listed.setUpdatedTime(LocalDateTime.of(2026, 11, 5, 11, 30, 1));

        CourseEntity off = outsideCourse("off", "passed", null);
        off.setTitle("已下架课");
        off.setUpdatedTime(LocalDateTime.of(2026, 11, 4, 10, 0, 0));

        CourseEntity noOwner = outsideCourse("on", "passed", null);
        noOwner.setTitle("无归属人");

        when(courseRepository.searchOutside(eq("数学"), eq(null), eq(true), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(listed, off, noOwner),
                        org.springframework.data.domain.PageRequest.of(0, 10),
                        3));

        CourseOwnerEntity owner = new CourseOwnerEntity();
        owner.setId(new CourseOwnerId(listed.getId(), OPERATOR_ID));
        owner.setSortOrder(0);
        when(ownerRepository.findByCourseIds(any())).thenReturn(List.of(owner));
        when(employeeDeptResolver.resolvePrimaryBatch(any()))
                .thenReturn(Map.of(OPERATOR_ID, new EmployeeDeptResolver.Dept(58L, "技术管理中心")));

        CourseAuditEntity audit = new CourseAuditEntity();
        audit.setCourseId(listed.getId());
        audit.setOperatorName("张三");
        audit.setCreatedTime(LocalDateTime.of(2026, 11, 5, 11, 0, 0));
        when(auditRepository.findByCourseIdInOrderByCreatedTimeDesc(any())).thenReturn(List.of(audit));

        CourseCatalogNodeEntity liveNode = new CourseCatalogNodeEntity();
        liveNode.setCourseId(listed.getId());
        liveNode.setContentKind(CourseCatalogContentKind.LIVE);
        liveNode.setContentId("act-live");
        CourseCatalogNodeEntity replayNode = new CourseCatalogNodeEntity();
        replayNode.setCourseId(listed.getId());
        replayNode.setContentKind(CourseCatalogContentKind.LIVE);
        replayNode.setContentId("act-replay");
        CourseCatalogNodeEntity missing = new CourseCatalogNodeEntity();
        missing.setCourseId(listed.getId());
        missing.setContentKind(CourseCatalogContentKind.LIVE);
        missing.setContentId("act-gone");
        when(catalogNodeRepository.findActiveByCourseIdsAndContentKind(any(), eq(CourseCatalogContentKind.LIVE)))
                .thenReturn(List.of(liveNode, replayNode, missing));

        ActivityEntity live = new ActivityEntity();
        live.setId("act-live");
        live.setType(2);
        ActivityEntity replay = new ActivityEntity();
        replay.setId("act-replay");
        replay.setType(4);
        ActivityEntity deleted = new ActivityEntity();
        deleted.setId("act-gone");
        deleted.setType(1);
        deleted.setDeletedTime(LocalDateTime.of(2026, 11, 1, 0, 0));
        when(activityRepository.findAllById(any())).thenReturn(List.of(live, replay, deleted));

        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setName(" 数学 ");
        OutsideCourseListResponse response = courseService.outsideList(request);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).searchOutside(eq("数学"), eq(null), eq(true), any(), pageable.capture());
        Sort.Order order = pageable.getValue().getSort().getOrderFor("updatedTime");
        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
        assertEquals(10, pageable.getValue().getPageSize());

        assertEquals(1, response.getPage());
        assertEquals(10, response.getPageSize());
        assertEquals(3, response.getTotal());
        assertEquals(3, response.getList().size());

        var first = response.getList().get(0);
        assertEquals(listed.getId(), first.getCourseId());
        assertEquals("已上架课", first.getCourseName());
        assertEquals(58L, first.getDeptId());
        assertEquals("技术管理中心", first.getDeptName());
        assertEquals(1, first.getStatus());
        assertEquals("张三", first.getUpdaterName());
        assertEquals("2026-11-05 11:30:01", first.getUpdateTime());
        assertEquals(1, first.getLiveCount());
        assertEquals(1, first.getReplayCount());
        assertNull(first.getTeacherName());

        var offItem = response.getList().get(1);
        assertEquals(off.getId(), offItem.getCourseId());
        assertEquals(2, offItem.getStatus());

        var bare = response.getList().get(2);
        assertEquals(noOwner.getId(), bare.getCourseId());
        assertNull(bare.getDeptId());
        assertNull(bare.getDeptName());

        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(response);
        for (String forbidden : List.of(
                "materialId", "materialName", "contentCount", "departmentName",
                "entitledUserCount", "totalUserCount", "salesCount",
                "riskStatus", "shelfStatus", "canList", "canUnlist", "canDelete",
                "detailHtml", "avatar", "owners", "coverUrl", "introduction", "listedTime",
                "\"title\"")) {
            assertFalse(json.contains(forbidden), forbidden);
        }
        String firstJson = mapper.writeValueAsString(first);
        assertTrue(firstJson.contains("\"courseId\""));
        assertTrue(firstJson.contains("\"courseName\":\"已上架课\""));
        assertTrue(firstJson.contains("\"deptId\":58"));
        assertTrue(firstJson.contains("\"deptName\":\"技术管理中心\""));
        assertTrue(firstJson.contains("\"teacherName\":null"));
        String bareJson = mapper.writeValueAsString(bare);
        assertFalse(bareJson.contains("\"deptId\""));
        assertFalse(bareJson.contains("\"deptName\""));
        assertTrue(bareJson.contains("\"teacherName\":null"));
    }

    @Test
    void outsideList_pageSizeOverMax_isClampedTo100() {
        when(courseRepository.searchOutside(eq(null), eq(null), eq(true), any(), any())).thenReturn(
                new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 100), 0));

        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setPage(0);
        request.setPageSize(500);
        OutsideCourseListResponse response = courseService.outsideList(request);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).searchOutside(eq(null), eq(null), eq(true), any(), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(1, response.getPage());
        assertEquals(100, response.getPageSize());
    }

    @Test
    void outsideList_nameLongerThan50_rejected() {
        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setName("a".repeat(51));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> courseService.outsideList(request));
        assertTrue(ex.getMessage().contains("50"));
        verify(courseRepository, never()).searchOutside(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void outsideList_invalidStatus_rejected() {
        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setStatus(3);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> courseService.outsideList(request));
        assertTrue(ex.getMessage().contains("状态无效"));
    }

    @Test
    void outsideList_status2_queriesOffOnly() {
        when(courseRepository.searchOutside(eq(null), eq(2), eq(true), any(), any())).thenReturn(
                new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0));
        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setStatus(2);
        courseService.outsideList(request);
        verify(courseRepository).searchOutside(eq(null), eq(2), eq(true), any(), any());
    }

    @Test
    void outsideList_deptId_filtersBeforePaging() {
        CourseOwnerEntity owner = new CourseOwnerEntity();
        owner.setId(new CourseOwnerId("course-dept", OPERATOR_ID));
        owner.setSortOrder(0);
        CourseOwnerEntity other = new CourseOwnerEntity();
        other.setId(new CourseOwnerId("course-other", 20002L));
        other.setSortOrder(0);
        when(ownerRepository.findAllOrdered()).thenReturn(List.of(owner, other));
        when(employeeDeptResolver.resolvePrimaryBatch(any())).thenReturn(Map.of(
                OPERATOR_ID, new EmployeeDeptResolver.Dept(58L, "技术管理中心"),
                20002L, new EmployeeDeptResolver.Dept(99L, "其他部门")));
        when(courseRepository.searchOutside(eq(null), eq(null), eq(false), eq(List.of("course-dept")), any()))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0));

        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setDeptId(58L);
        courseService.outsideList(request);

        verify(courseRepository).searchOutside(
                eq(null), eq(null), eq(false), eq(List.of("course-dept")), any());
    }

    @Test
    void outsideList_deptId_noMatch_returnsEmptyWithoutQuery() {
        when(ownerRepository.findAllOrdered()).thenReturn(List.of());
        OutsideCourseListRequest request = new OutsideCourseListRequest();
        request.setDeptId(58L);
        OutsideCourseListResponse response = courseService.outsideList(request);
        assertEquals(0, response.getTotal());
        assertEquals(10, response.getPageSize());
        verify(courseRepository, never()).searchOutside(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void delete_softDeletesRegardlessOfContentOrSales() {
        CourseEntity withSales = stored("pending", "pending", true);
        withSales.setSalesCount(1);
        withSales.setContentCount(3);
        courseService.delete(withSales.getId());
        assertNotNull(withSales.getDeletedTime());

        CourseEntity empty = stored("pending", "pending", false);
        courseService.delete(empty.getId());
        assertNotNull(empty.getDeletedTime());
    }

    @Test
    void resolveAccessPermission_archiveForcedDownloadOnly() {
        assertEquals(3, CourseService.resolveAccessPermission("a.zip", null));
        assertEquals(3, CourseService.resolveAccessPermission("a.ZIP", 3));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CourseService.resolveAccessPermission("pack.rar", 1));
        assertTrue(ex.getMessage().contains("压缩包"));
    }

    @Test
    void resolveAccessPermission_previewableDefaultsAndRejectsDownloadOnly() {
        assertEquals(1, CourseService.resolveAccessPermission("a.png", null));
        assertEquals(2, CourseService.resolveAccessPermission("a.png", 2));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CourseService.resolveAccessPermission("doc.pdf", 3));
        assertTrue(ex.getMessage().contains("仅下载"));
    }

    private CreateCourseRequest request(boolean listed) {
        CreateCourseRequest request = new CreateCourseRequest();
        request.setTitle(" 新课程 ");
        request.setCoverMaterialId("cover-1");
        request.setListed(listed);
        request.setOwnerIds(List.of(OPERATOR_ID));
        return request;
    }

    private UpdateCourseRequest updateRequest(boolean listed) {
        UpdateCourseRequest request = new UpdateCourseRequest();
        request.setTitle(" 新课程 ");
        request.setCoverMaterialId("cover-1");
        request.setListed(listed);
        request.setOwnerIds(List.of(OPERATOR_ID));
        request.setMaterials(List.of());
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
