package com.zhibo.admin.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityCommentRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAudienceServiceTest {

    private static final String ACTIVITY_ID = "b".repeat(32);

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ActivityCommentRepository commentRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    private ActivityAudienceService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAudienceService(
                activityRepository,
                audienceRepository,
                commentRepository,
                viewerRepository,
                userRepository);
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    @Test
    void list_showsEmployeeInviter() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(1L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);
        row.setEmployeeInviterId(100L);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setNickname("wyh");

        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("王富贵");
        emp.setRsStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(userRepository.findAllById(any())).thenReturn(List.of(emp));

        var resp = service.list(ACTIVITY_ID, 1, 20, null, null, null, null);
        assertEquals(1, resp.getList().size());
        assertEquals("employee", resp.getList().get(0).getInviterKind());
        assertEquals("王富贵", resp.getList().get(0).getInviterDisplayName());
    }

    @Test
    void list_emptyInviterWhenOnlyAudienceInviteOrImport() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(2L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);
        row.setImporterId(200L);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setNickname("wyh");

        SysUserAccountEntity importer = new SysUserAccountEntity();
        importer.setId(200L);
        importer.setUserName("导入员");
        importer.setRsStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(userRepository.findAllById(any())).thenReturn(List.of(importer));

        var resp = service.list(ACTIVITY_ID, 1, 20, null, null, null, null);
        assertNull(resp.getList().get(0).getInviterKind());
        assertNull(resp.getList().get(0).getInviterDisplayName());
        assertEquals("导入员", resp.getList().get(0).getImporterName());
    }
}
