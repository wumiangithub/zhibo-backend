package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    @Test
    void report_persistsComment() {
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setGuestId("guest-1");
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

        var resp = service.report(ACTIVITY_ID, request);
        assertEquals(9L, resp.getId());

        ArgumentCaptor<RoomCommentEntity> cap = ArgumentCaptor.forClass(RoomCommentEntity.class);
        verify(commentRepository).save(cap.capture());
        assertEquals("hello", cap.getValue().getContent());
        assertEquals("guest-1", cap.getValue().getGuestId());
        assertEquals("msg-1", cap.getValue().getProviderMsgId());
    }

    @Test
    void report_idempotentByProviderMsgId() {
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        when(commentRepository.findByActivityIdAndProviderMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.of(existing));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setGuestId("guest-1");
        request.setProviderMsgId("msg-1");

        assertEquals(3L, service.report(ACTIVITY_ID, request).getId());
    }

    @Test
    void report_idempotentBackfillsViewerId() {
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        existing.setViewerId(null);
        when(commentRepository.findByActivityIdAndProviderMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.of(existing));
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setGuestId("guest-1");
        request.setViewerId(12L);
        request.setProviderMsgId("msg-1");

        assertEquals(3L, service.report(ACTIVITY_ID, request).getId());
        assertEquals(12L, existing.getViewerId());
        verify(commentRepository).save(existing);
    }

    @Test
    void report_requiresGuestOrViewer() {
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("x");
        assertThrows(IllegalArgumentException.class, () -> service.report(ACTIVITY_ID, request));
    }
}
