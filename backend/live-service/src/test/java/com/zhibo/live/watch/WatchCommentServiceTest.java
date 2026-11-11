package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import com.zhibo.live.watch.dto.UpsertQuestionAnswerRequest;
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
    private static final String PROVIDER = "vhall";
    private static final String ROOM_ID = "webinar-99";

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
        activity.setProvider(PROVIDER);
        activity.setProviderRoomId(ROOM_ID);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    @Test
    void report_withoutMsgIdInsertsRow() {
        stubActivity();
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setNickname("wyh");

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
        assertEquals(12L, cap.getValue().getUid());
        assertNull(cap.getValue().getMsgId());
        assertNull(cap.getValue().getQuestionId());
        assertEquals(PROVIDER, cap.getValue().getProvider());
        assertEquals(ROOM_ID, cap.getValue().getRoomId());
        assertEquals(RoomCommentType.CHAT, cap.getValue().getCommentType());
    }

    @Test
    void report_questionStoresQuestionIdNotMsgId() {
        stubActivity();
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("这是提问");
        request.setCommentType(RoomCommentType.QUESTION);
        request.setQuestionId("2397558");

        when(commentRepository.findByActivityIdAndQuestionId(ACTIVITY_ID, "2397558"))
                .thenReturn(Optional.empty());
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> {
            RoomCommentEntity e = inv.getArgument(0);
            e.setId(11L);
            return e;
        });

        assertEquals(11L, service.report(ACTIVITY_ID, 12L, request).getId());

        ArgumentCaptor<RoomCommentEntity> cap = ArgumentCaptor.forClass(RoomCommentEntity.class);
        verify(commentRepository).save(cap.capture());
        assertEquals(RoomCommentType.QUESTION, cap.getValue().getCommentType());
        assertEquals("这是提问", cap.getValue().getContent());
        assertEquals("2397558", cap.getValue().getQuestionId());
        assertNull(cap.getValue().getMsgId());
        assertEquals(PROVIDER, cap.getValue().getProvider());
        assertEquals(ROOM_ID, cap.getValue().getRoomId());
    }

    @Test
    void report_questionRequiresQuestionId() {
        stubActivity();
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("这是提问");
        request.setCommentType(RoomCommentType.QUESTION);
        assertThrows(IllegalArgumentException.class, () -> service.report(ACTIVITY_ID, 12L, request));
    }

    @Test
    void report_rejectsUnknownCommentType() {
        stubActivity();
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setCommentType(9);
        assertThrows(IllegalArgumentException.class, () -> service.report(ACTIVITY_ID, 12L, request));
    }

    @Test
    void report_persistsCommentWithJwtViewerId() {
        stubActivity();
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setNickname("wyh");
        request.setMsgId("msg-1");
        request.setCommentTime("2026-10-27 12:00:00");

        when(commentRepository.findByActivityIdAndMsgId(ACTIVITY_ID, "msg-1"))
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
        assertEquals(12L, cap.getValue().getUid());
        assertNull(cap.getValue().getGuestId());
        assertEquals("msg-1", cap.getValue().getMsgId());
        assertEquals(PROVIDER, cap.getValue().getProvider());
        assertEquals(ROOM_ID, cap.getValue().getRoomId());
    }

    @Test
    void report_idempotentByMsgId() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        existing.setUid(12L);
        existing.setProvider(PROVIDER);
        existing.setRoomId(ROOM_ID);
        when(commentRepository.findByActivityIdAndMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.of(existing));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setMsgId("msg-1");

        assertEquals(3L, service.report(ACTIVITY_ID, 12L, request).getId());
    }

    @Test
    void report_questionIdempotentBackfillsMsgId() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        existing.setUid(12L);
        existing.setQuestionId("2397558");
        when(commentRepository.findByActivityIdAndQuestionId(ACTIVITY_ID, "2397558"))
                .thenReturn(Optional.of(existing));
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("这是提问");
        request.setCommentType(RoomCommentType.QUESTION);
        request.setQuestionId("2397558");
        request.setMsgId("msg_d10f061ca8275a064f5fff8fd7d852ea");

        assertEquals(3L, service.report(ACTIVITY_ID, 12L, request).getId());
        assertEquals("msg_d10f061ca8275a064f5fff8fd7d852ea", existing.getMsgId());
        assertEquals(PROVIDER, existing.getProvider());
        assertEquals(ROOM_ID, existing.getRoomId());
        verify(commentRepository).save(existing);
    }

    @Test
    void report_idempotentBackfillsViewerId() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(3L);
        existing.setUid(null);
        when(commentRepository.findByActivityIdAndMsgId(ACTIVITY_ID, "msg-1"))
                .thenReturn(Optional.of(existing));
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("hello");
        request.setMsgId("msg-1");

        assertEquals(3L, service.report(ACTIVITY_ID, 12L, request).getId());
        assertEquals(12L, existing.getUid());
        assertEquals(PROVIDER, existing.getProvider());
        assertEquals(ROOM_ID, existing.getRoomId());
        verify(commentRepository).save(existing);
    }

    @Test
    void report_rejectsInvalidViewerId() {
        ReportCommentRequest request = new ReportCommentRequest();
        request.setContent("x");
        assertThrows(IllegalArgumentException.class, () -> service.report(ACTIVITY_ID, 0L, request));
    }

    @Test
    void upsertQuestionAnswer_createsRowWhenMissing() {
        stubActivity();
        when(commentRepository.findByActivityIdAndQuestionId(ACTIVITY_ID, "2397558"))
                .thenReturn(Optional.empty());
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> {
            RoomCommentEntity e = inv.getArgument(0);
            e.setId(21L);
            return e;
        });

        UpsertQuestionAnswerRequest request = new UpsertQuestionAnswerRequest();
        request.setQuestionId("2397558");
        request.setMsgId("msg_d10f061ca8275a064f5fff8fd7d852ea");
        request.setContent("请问开课时间？");
        request.setNickname("学员A");
        request.setRoleName(2);
        request.setAnswerContent("明天晚上 8 点");
        request.setAnswerNickname("老师");
        request.setAnswerRoleName(1);

        assertEquals(21L, service.upsertQuestionAnswer(ACTIVITY_ID, request).getId());

        ArgumentCaptor<RoomCommentEntity> cap = ArgumentCaptor.forClass(RoomCommentEntity.class);
        verify(commentRepository).save(cap.capture());
        RoomCommentEntity saved = cap.getValue();
        assertEquals(RoomCommentType.QUESTION, saved.getCommentType());
        assertEquals("2397558", saved.getQuestionId());
        assertEquals("msg_d10f061ca8275a064f5fff8fd7d852ea", saved.getMsgId());
        assertEquals("请问开课时间？", saved.getContent());
        assertEquals("明天晚上 8 点", saved.getAnswerContent());
        assertEquals("老师", saved.getAnswerNickname());
        assertEquals(1, saved.getAnswerRoleName());
        assertEquals(PROVIDER, saved.getProvider());
        assertEquals(ROOM_ID, saved.getRoomId());
        assertNull(saved.getUid());
    }

    @Test
    void upsertQuestionAnswer_idempotentSecondCall() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(21L);
        existing.setCommentType(RoomCommentType.QUESTION);
        existing.setQuestionId("2397558");
        existing.setMsgId("msg_abc");
        existing.setProvider(PROVIDER);
        existing.setRoomId(ROOM_ID);
        existing.setContent("请问开课时间？");
        existing.setAnswerContent("明天晚上 8 点");
        existing.setAnswerNickname("老师");
        existing.setAnswerRoleName(1);
        when(commentRepository.findByActivityIdAndQuestionId(ACTIVITY_ID, "2397558"))
                .thenReturn(Optional.of(existing));

        UpsertQuestionAnswerRequest request = new UpsertQuestionAnswerRequest();
        request.setQuestionId("2397558");
        request.setMsgId("msg_abc");
        request.setContent("请问开课时间？");
        request.setAnswerContent("明天晚上 8 点");
        request.setAnswerNickname("老师");
        request.setAnswerRoleName(1);

        assertEquals(21L, service.upsertQuestionAnswer(ACTIVITY_ID, request).getId());
        assertEquals(21L, service.upsertQuestionAnswer(ACTIVITY_ID, request).getId());
        verify(commentRepository, never()).save(any());
    }

    @Test
    void upsertQuestionAnswer_updatesWhenAnswerChanges() {
        stubActivity();
        RoomCommentEntity existing = new RoomCommentEntity();
        existing.setId(21L);
        existing.setCommentType(RoomCommentType.QUESTION);
        existing.setQuestionId("2397558");
        existing.setProvider(PROVIDER);
        existing.setRoomId(ROOM_ID);
        existing.setContent("请问开课时间？");
        existing.setAnswerContent("旧回复");
        when(commentRepository.findByActivityIdAndQuestionId(ACTIVITY_ID, "2397558"))
                .thenReturn(Optional.of(existing));
        when(commentRepository.save(any(RoomCommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpsertQuestionAnswerRequest request = new UpsertQuestionAnswerRequest();
        request.setQuestionId("2397558");
        request.setAnswerContent("新回复");
        request.setAnswerNickname("助理");
        request.setAnswerRoleName(3);

        assertEquals(21L, service.upsertQuestionAnswer(ACTIVITY_ID, request).getId());
        assertEquals("新回复", existing.getAnswerContent());
        assertEquals("助理", existing.getAnswerNickname());
        assertEquals(3, existing.getAnswerRoleName());
        verify(commentRepository, times(1)).save(existing);
    }

    @Test
    void upsertQuestionAnswer_rejectsMissingContentWhenNoRow() {
        stubActivity();
        when(commentRepository.findByActivityIdAndQuestionId(ACTIVITY_ID, "2397558"))
                .thenReturn(Optional.empty());

        UpsertQuestionAnswerRequest request = new UpsertQuestionAnswerRequest();
        request.setQuestionId("2397558");
        request.setAnswerContent("回复");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.upsertQuestionAnswer(ACTIVITY_ID, request));
    }
}
