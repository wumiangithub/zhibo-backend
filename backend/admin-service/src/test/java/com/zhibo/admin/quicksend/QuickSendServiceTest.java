package com.zhibo.admin.quicksend;

import com.zhibo.admin.quicksend.dto.BatchDeleteQuickSendMessagesRequest;
import com.zhibo.admin.quicksend.dto.CreateQuickSendGroupRequest;
import com.zhibo.admin.quicksend.dto.CreateQuickSendMessageRequest;
import com.zhibo.admin.quicksend.dto.QuickSendGroupResponse;
import com.zhibo.admin.quicksend.dto.QuickSendMessageResponse;
import com.zhibo.admin.quicksend.dto.ReorderQuickSendMessagesRequest;
import com.zhibo.admin.quicksend.dto.UpdateQuickSendMessageRequest;
import com.zhibo.quicksend.QuickSendGroupEntity;
import com.zhibo.quicksend.QuickSendGroupRepository;
import com.zhibo.quicksend.QuickSendMessageEntity;
import com.zhibo.quicksend.QuickSendMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickSendServiceTest {

    private static final String GROUP_ID = "b0000000000000000000000000000001";
    private static final String MSG_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String MSG_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String MSG_C = "cccccccccccccccccccccccccccccccc";

    @Mock
    private QuickSendGroupRepository groupRepository;
    @Mock
    private QuickSendMessageRepository messageRepository;

    @InjectMocks
    private QuickSendService service;

    @Test
    void listGroups_returnsCount() {
        QuickSendGroupEntity g = group(GROUP_ID, "默认", true, 0);
        when(groupRepository.findAllByOrderByDefaultGroupDescSortOrderAscIdAsc())
                .thenReturn(List.of(g));
        when(messageRepository.countByGroupIdAndDeletedTimeIsNull(GROUP_ID)).thenReturn(3L);

        List<QuickSendGroupResponse> list = service.listGroups();
        assertEquals(1, list.size());
        assertEquals(3L, list.get(0).getCount());
        assertTrue(list.get(0).isDefault());
    }

    @Test
    void createGroup_rejectsDuplicateName() {
        CreateQuickSendGroupRequest req = new CreateQuickSendGroupRequest();
        req.setName("促销");
        when(groupRepository.existsByName("促销")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createGroup(req));
    }

    @Test
    void deleteGroup_rejectsDefault() {
        when(groupRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(group(GROUP_ID, "默认", true, 0)));
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.deleteGroup(GROUP_ID));
        assertEquals("默认分组不可删除", ex.getMessage());
    }

    @Test
    void createMessage_insertsBelowAnchorAndRenumbers() {
        when(groupRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(group(GROUP_ID, "默认", true, 0)));
        QuickSendMessageEntity a = message(MSG_A, GROUP_ID, "一", 1);
        QuickSendMessageEntity b = message(MSG_B, GROUP_ID, "二", 2);
        when(messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(GROUP_ID))
                .thenReturn(new ArrayList<>(List.of(a, b)));
        when(messageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateQuickSendMessageRequest req = new CreateQuickSendMessageRequest();
        req.setGroupId(GROUP_ID);
        req.setContent("新");
        req.setAnchorId(MSG_A);
        req.setPlace("below");

        QuickSendMessageResponse created = service.createMessage(req);
        assertEquals("新", created.getContent());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuickSendMessageEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());
        List<QuickSendMessageEntity> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertEquals(1, saved.get(0).getSortOrder());
        assertEquals(MSG_A, saved.get(0).getId());
        assertEquals(2, saved.get(1).getSortOrder());
        assertEquals("新", saved.get(1).getContent());
        assertEquals(3, saved.get(2).getSortOrder());
        assertEquals(MSG_B, saved.get(2).getId());
    }

    @Test
    void createMessage_rejectsOverlongContent() {
        when(groupRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(group(GROUP_ID, "默认", true, 0)));
        CreateQuickSendMessageRequest req = new CreateQuickSendMessageRequest();
        req.setGroupId(GROUP_ID);
        req.setContent("x".repeat(101));
        assertThrows(IllegalArgumentException.class, () -> service.createMessage(req));
        verify(messageRepository, never()).saveAll(any());
    }

    @Test
    void updateMessage_updatesContent() {
        QuickSendMessageEntity m = message(MSG_A, GROUP_ID, "旧", 1);
        when(messageRepository.findByIdAndDeletedTimeIsNull(MSG_A)).thenReturn(Optional.of(m));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateQuickSendMessageRequest req = new UpdateQuickSendMessageRequest();
        req.setContent("新文案");
        QuickSendMessageResponse out = service.updateMessage(MSG_A, req);
        assertEquals("新文案", out.getContent());
    }

    @Test
    void reorder_rejectsIncompleteIds() {
        when(groupRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(group(GROUP_ID, "默认", true, 0)));
        when(messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(GROUP_ID))
                .thenReturn(List.of(message(MSG_A, GROUP_ID, "一", 1), message(MSG_B, GROUP_ID, "二", 2)));
        ReorderQuickSendMessagesRequest req = new ReorderQuickSendMessagesRequest();
        req.setGroupId(GROUP_ID);
        req.setIds(List.of(MSG_A));
        assertThrows(IllegalArgumentException.class, () -> service.reorderMessages(req));
    }

    @Test
    void reorder_writesNewOrder() {
        when(groupRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(group(GROUP_ID, "默认", true, 0)));
        QuickSendMessageEntity a = message(MSG_A, GROUP_ID, "一", 1);
        QuickSendMessageEntity b = message(MSG_B, GROUP_ID, "二", 2);
        when(messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(GROUP_ID))
                .thenReturn(List.of(a, b));
        when(messageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ReorderQuickSendMessagesRequest req = new ReorderQuickSendMessagesRequest();
        req.setGroupId(GROUP_ID);
        req.setIds(List.of(MSG_B, MSG_A));
        service.reorderMessages(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuickSendMessageEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());
        assertEquals(MSG_B, captor.getValue().get(0).getId());
        assertEquals(1, captor.getValue().get(0).getSortOrder());
        assertEquals(MSG_A, captor.getValue().get(1).getId());
        assertEquals(2, captor.getValue().get(1).getSortOrder());
    }

    @Test
    void batchDelete_softDeletesFound() {
        QuickSendMessageEntity a = message(MSG_A, GROUP_ID, "一", 1);
        when(messageRepository.findByIdInAndDeletedTimeIsNull(anyCollection()))
                .thenReturn(List.of(a));
        when(messageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchDeleteQuickSendMessagesRequest req = new BatchDeleteQuickSendMessagesRequest();
        req.setIds(List.of(MSG_A, MSG_C));
        assertEquals(1, service.batchDelete(req).getDeleted());
        assertTrue(a.getDeletedTime() != null);
    }

    @Test
    void listMessages_pages() {
        when(groupRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(group(GROUP_ID, "默认", true, 0)));
        QuickSendMessageEntity a = message(MSG_A, GROUP_ID, "一", 1);
        when(messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(
                        eq(GROUP_ID), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 20), 1));
        var page = service.listMessages(GROUP_ID, 1, 20);
        assertEquals(1, page.getTotal());
        assertEquals("一", page.getList().get(0).getContent());
        assertFalse(page.getList().isEmpty());
    }

    private static QuickSendGroupEntity group(String id, String name, boolean def, int sort) {
        QuickSendGroupEntity g = new QuickSendGroupEntity();
        g.setId(id);
        g.setName(name);
        g.setDefaultGroup(def);
        g.setSortOrder(sort);
        return g;
    }

    private static QuickSendMessageEntity message(String id, String groupId, String content, int sort) {
        QuickSendMessageEntity m = new QuickSendMessageEntity();
        m.setId(id);
        m.setGroupId(groupId);
        m.setContent(content);
        m.setSortOrder(sort);
        return m;
    }
}
