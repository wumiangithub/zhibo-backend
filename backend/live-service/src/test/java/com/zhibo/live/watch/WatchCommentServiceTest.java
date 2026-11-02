package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchCommentServiceTest {

    private static final String ACTIVITY_ID = "a".repeat(32);

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private RoomCommentRepository commentRepository;

    private WatchCommentService service;

    @BeforeEach
    void setUp() {
        service = new WatchCommentService(activityRepository, commentRepository);
    }

    private void stubActivity() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    @Test
    void report_persistsCommentWithJwtViewerId() {
        stubActivity();
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setNickname("wyh");
        request.setProviderMsgId("msg-1");
        request.setCommentTime("2026-10-27 12:00:00");

        when(commentRepository.findByActivityIdAndProviderMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.empty());
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> {
            RoomCommentEntity e = inv.getArgument(0);
            e.setId(9L);
            return e;
        });

        var resp = service.report(ACTIVITY_ID, 12L, request);
        assertEquals(9L, resp.getId());

        ArgumentCaptor<RoomCommentEntity> cap = ArgumentCaptor.forClass(RoomCommentEntity.class);
        verify(commentRepository).save(cap.capture());
        assertEquals("hello", cap.getValue().getContent());
        assertEquals(12L, cap.getValue().getViewerId());
        assertNull(cap.getValue().getGuestId());
        assertEquals("msg-1", cap.getValue().getProviderMsgId());
    }

    @Test
    void report_idempotentByProviderMsgId() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        existing.setViewerId(12L);
        when(commentRepository.findByActivityIdAndProviderMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.of(existing));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setProviderMsgId("msg-1");

        assertEquals(3L, service.report(ACTIVITY_ID, 12L, request).getId());
    }

    @Test
    void report_idempotentBackfillsViewerId() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        existing.setViewerId(null);
        when(commentRepository.findByActivityIdAndProviderMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.of(existing));
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setProviderMsgId("msg-1");

        assertEquals(3L, service.report(ACTIVITY_ID, 12L, request).getId());
        assertEquals(12L, existing.getViewerId());
        verify(commentRepository).save(existing);
    }

    @Test
    void report_rejectsInvalidViewerId() {
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("x");
        assertThrows(IllegalArgumentException.class, () -> service.report(ACTIVITY_ID, 0L, request));
    }
}
