package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.live.watch.dto.WatchQuestionHistoryResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchQuestionHistoryServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private WatchService watchService;
    @Mock
    private RoomCommentRepository commentRepository;

    @InjectMocks
    private WatchQuestionHistoryService service;

    @Test
    void list_mapsQuestionAndAnswer() {
        ActivityEntity entity = activity();
        when(watchService.requireActivity(ID)).thenReturn(entity);

        RoomCommentEntity row = new RoomCommentEntity();
        row.setQuestionId("2397558");
        row.setViewerId(12L);
        row.setNickname("甲");
        row.setContent("怎么报名");
        row.setMsgId("msg_q1");
        row.setRoleName(2);
        row.setCommentType(RoomCommentType.QUESTION);
        row.setCommentTime(LocalDateTime.of(2026, 11, 6, 10, 0, 0));
        row.setAnswerContent("点链接");
        row.setAnswerNickname("主持");
        row.setAnswerRoleName(1);

        when(commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        eq(ID), eq(RoomCommentType.QUESTION)))
                .thenReturn(List.of(row));

        WatchQuestionHistoryResponse res = service.list(ID);
        assertEquals(1, res.getTotal());
        assertEquals(1, res.getList().size());
        assertEquals("2397558", res.getList().get(0).getQuestionId());
        assertEquals("12", res.getList().get(0).getSenderId());
        assertEquals("甲", res.getList().get(0).getSenderName());
        assertEquals("怎么报名", res.getList().get(0).getMsg());
        assertEquals("msg_q1", res.getList().get(0).getMsgId());
        assertEquals(2, res.getList().get(0).getRoleName());
        assertEquals("点链接", res.getList().get(0).getAnswerContent());
        assertEquals("主持", res.getList().get(0).getAnswerNickname());
        assertEquals(1, res.getList().get(0).getAnswerRoleName());
        verify(watchService).requireReadyForWatch(entity);
    }

    @Test
    void list_skipsBlankContent() {
        ActivityEntity entity = activity();
        when(watchService.requireActivity(ID)).thenReturn(entity);

        RoomCommentEntity blank = new RoomCommentEntity();
        blank.setQuestionId("1");
        blank.setNickname("甲");
        blank.setContent("  ");
        blank.setCommentTime(LocalDateTime.now());

        when(commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        eq(ID), eq(RoomCommentType.QUESTION)))
                .thenReturn(List.of(blank));

        WatchQuestionHistoryResponse res = service.list(ID);
        assertEquals(1, res.getTotal());
        assertTrue(res.getList().isEmpty());
    }

    private static ActivityEntity activity() {
        ActivityEntity e = new ActivityEntity();
        e.setId(ID);
        e.setProvider("vhall");
        e.setProviderRoomId("1001");
        return e;
    }
}
