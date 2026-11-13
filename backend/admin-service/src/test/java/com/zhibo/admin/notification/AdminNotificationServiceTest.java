package com.zhibo.admin.notification;

import com.zhibo.admin.notification.dto.NotificationIdResponse;
import com.zhibo.admin.notification.dto.NotificationListResponse;
import com.zhibo.admin.notification.dto.NotificationReadAllResponse;
import com.zhibo.admin.notification.dto.NotificationUnreadCountResponse;
import com.zhibo.notification.AdminNotificationAuditResult;
import com.zhibo.notification.AdminNotificationEntity;
import com.zhibo.notification.AdminNotificationInboxEntity;
import com.zhibo.notification.AdminNotificationInboxId;
import com.zhibo.notification.AdminNotificationInboxRepository;
import com.zhibo.notification.AdminNotificationRefType;
import com.zhibo.notification.AdminNotificationRepository;
import com.zhibo.notification.AdminNotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant FIXED = Instant.parse("2026-11-13T05:00:00Z");

    @Mock
    private AdminNotificationRepository notificationRepository;
    @Mock
    private AdminNotificationInboxRepository inboxRepository;

    private AdminNotificationService service;

    @BeforeEach
    void setUp() {
        service = new AdminNotificationService(
                notificationRepository,
                inboxRepository,
                Clock.fixed(FIXED, SHANGHAI));
    }

    @Test
    void create_withRecipients_writesNotificationAndInbox() {
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            AdminNotificationEntity e = inv.getArgument(0);
            e.setId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            return e;
        });

        service.create(
                AdminNotificationType.MATERIAL_AUDIT,
                AdminNotificationAuditResult.PASSED,
                "素材库",
                "合规风控审核结果通知",
                "风控合规-已通过：素材「封面」",
                AdminNotificationRefType.MATERIAL,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                List.of(10001L, 10001L, 10002L));

        ArgumentCaptor<AdminNotificationEntity> noticeCap =
                ArgumentCaptor.forClass(AdminNotificationEntity.class);
        verify(notificationRepository).save(noticeCap.capture());
        assertEquals(AdminNotificationType.MATERIAL_AUDIT, noticeCap.getValue().getType());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminNotificationInboxEntity>> inboxCap =
                ArgumentCaptor.forClass(List.class);
        verify(inboxRepository).saveAll(inboxCap.capture());
        assertEquals(2, inboxCap.getValue().size());
    }

    @Test
    void create_noRecipients_skipsWrite() {
        service.create(
                AdminNotificationType.LIVE_AUDIT,
                AdminNotificationAuditResult.PASSED,
                "直播",
                "合规风控审核结果通知",
                "body",
                AdminNotificationRefType.ACTIVITY,
                "cccccccccccccccccccccccccccccccc",
                List.of());

        verify(notificationRepository, never()).save(any());
        verify(inboxRepository, never()).saveAll(anyCollection());
    }

    @Test
    void notifyQuietly_swallowsFailure() {
        when(notificationRepository.save(any())).thenThrow(new RuntimeException("db down"));

        service.notifyQuietly(
                AdminNotificationType.LIVE_REPORT_AUDIT,
                AdminNotificationAuditResult.REJECTED,
                "直播报备",
                "合规风控审核结果通知",
                "审核-已驳回",
                AdminNotificationRefType.LIVE_REPORT,
                "dddddddddddddddddddddddddddddddd",
                List.of(8L));
    }

    @Test
    void notifyMaterialAuditQuietly_noUploader_skips() {
        service.notifyMaterialAuditQuietly(
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "封面", "risk", 1, null);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void list_filtersAndMapsUnread() {
        AdminNotificationEntity row = new AdminNotificationEntity();
        row.setId("ffffffffffffffffffffffffffffffff");
        row.setType(AdminNotificationType.LIVE_AUDIT);
        row.setAuditResult(AdminNotificationAuditResult.PASSED);
        row.setCategory("直播");
        row.setTitle("合规风控审核结果通知");
        row.setBody("直播「公开课」审核通过，可正常开播。");
        row.setRefType(AdminNotificationRefType.ACTIVITY);
        row.setRefId("11111111111111111111111111111111");
        row.setCreatedTime(LocalDateTime.of(2026, 11, 13, 13, 0, 0));

        when(notificationRepository.searchForStaff(
                        eq(10001L), isNull(), eq(1), isNull(), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));
        when(inboxRepository.findByIdStaffIdAndIdNotificationIdIn(
                        eq(10001L), eq(List.of("ffffffffffffffffffffffffffffffff"))))
                .thenReturn(List.of(inbox("ffffffffffffffffffffffffffffffff", 10001L, null)));

        NotificationListResponse resp = service.list(10001L, 1, 10, null, 0, 1);

        assertEquals(1, resp.getTotal());
        assertEquals(1, resp.getList().size());
        assertTrue(resp.getList().get(0).getUnread());
        assertEquals("直播", resp.getList().get(0).getCategory());
    }

    @Test
    void list_rejectsBadPage() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.list(1L, 0, 10, null, null, null));
        assertEquals("page 不能小于 1", ex.getMessage());
    }

    @Test
    void unreadCount_delegates() {
        when(inboxRepository.countByIdStaffIdAndReadTimeIsNull(9L)).thenReturn(5L);
        NotificationUnreadCountResponse resp = service.unreadCount(9L);
        assertEquals(5L, resp.getCount());
    }

    @Test
    void readOne_marksUnread() {
        AdminNotificationInboxEntity inbox =
                inbox("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 3L, null);
        when(inboxRepository.findByIdNotificationIdAndIdStaffId(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 3L))
                .thenReturn(Optional.of(inbox));

        NotificationIdResponse resp =
                service.readOne(3L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", resp.getId());
        assertEquals(LocalDateTime.of(2026, 11, 13, 13, 0, 0), inbox.getReadTime());
        verify(inboxRepository).save(inbox);
    }

    @Test
    void readOne_missing_throws() {
        when(inboxRepository.findByIdNotificationIdAndIdStaffId(any(), anyLong()))
                .thenReturn(Optional.empty());
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.readOne(3L, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        assertEquals("通知不存在", ex.getMessage());
    }

    @Test
    void readAll_returnsMarkedCount() {
        when(inboxRepository.markAllRead(eq(3L), any())).thenReturn(4);
        NotificationReadAllResponse resp = service.readAll(3L);
        assertEquals(4L, resp.getCount());
    }

    @Test
    void notifyLiveAuditQuietly_mapsPassedBody() {
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            AdminNotificationEntity e = inv.getArgument(0);
            e.setId("cccccccccccccccccccccccccccccccc");
            return e;
        });

        service.notifyLiveAuditQuietly(
                "dddddddddddddddddddddddddddddddd",
                "十月公开课",
                "passed",
                List.of(10001L));

        ArgumentCaptor<AdminNotificationEntity> cap =
                ArgumentCaptor.forClass(AdminNotificationEntity.class);
        verify(notificationRepository).save(cap.capture());
        assertEquals(AdminNotificationType.LIVE_AUDIT, cap.getValue().getType());
        assertTrue(cap.getValue().getBody().contains("审核通过"));
        assertFalse(cap.getValue().getBody().contains("未通过"));
    }

    private static AdminNotificationInboxEntity inbox(
            String notificationId, long staffId, LocalDateTime readTime) {
        AdminNotificationInboxEntity row = new AdminNotificationInboxEntity();
        row.setId(new AdminNotificationInboxId(notificationId, staffId));
        row.setReadTime(readTime);
        return row;
    }
}
