package com.zhibo.admin.viewertag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.viewertag.dto.CreateViewerTagGroupRequest;
import com.zhibo.admin.viewertag.dto.CreateViewerTagRequest;
import com.zhibo.admin.viewertag.dto.UpdateViewerTagRequest;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupItem;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupPageItem;
import com.zhibo.admin.viewertag.dto.ViewerTagItem;
import com.zhibo.admin.viewertag.dto.ViewerTagListResponse;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagGroupEntity;
import com.zhibo.viewer.ViewerTagGroupRepository;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import com.zhibo.viewer.ViewerTagType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class ViewerTagAdminServiceTest {

    private ViewerTagRepository tagRepository;
    private ViewerTagGroupRepository groupRepository;
    private ViewerTagMemberRepository memberRepository;
    private SysUserAccountRepository userRepository;
    private ViewerTagAdminService service;

    @BeforeEach
    void setUp() {
        tagRepository = mock(ViewerTagRepository.class);
        groupRepository = mock(ViewerTagGroupRepository.class);
        memberRepository = mock(ViewerTagMemberRepository.class);
        userRepository = mock(SysUserAccountRepository.class);
        service = new ViewerTagAdminService(tagRepository, groupRepository, memberRepository, userRepository);
    }

    @Test
    void listGroups_mapsDefault() {
        ViewerTagGroupEntity g = new ViewerTagGroupEntity();
        g.setId(1L);
        g.setName("默认分组");
        g.setIsDefault(1);
        when(groupRepository.findByDeletedTimeIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(g));

        List<ViewerTagGroupItem> list = service.listGroups();
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).getId());
        assertTrue(list.get(0).isDefault());
    }

    @Test
    void list_mapsFields() {
        ViewerTagEntity tag = baseTag(9L, "测试标签1", ViewerTagType.MANUAL, 1L);
        when(tagRepository.findAll(ArgumentMatchers.<Specification<ViewerTagEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tag)));
        ViewerTagGroupEntity g = new ViewerTagGroupEntity();
        g.setId(1L);
        g.setName("默认分组");
        when(groupRepository.findAllById(any())).thenReturn(List.of(g));
        when(memberRepository.countGroupedByTagId(any())).thenReturn(List.of());

        ViewerTagListResponse res = service.list(1, 10, null, null, null);
        assertEquals(1, res.getTotal());
        assertEquals("测试标签1", res.getList().get(0).getName());
        assertEquals("默认分组", res.getList().get(0).getGroupName());
        assertEquals(0L, res.getList().get(0).getUserCount());
    }

    @Test
    void create_rejectsAuto() {
        CreateViewerTagRequest req = new CreateViewerTagRequest();
        req.setName("自动");
        req.setType(ViewerTagType.AUTO);
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void create_manualOk() {
        CreateViewerTagRequest req = new CreateViewerTagRequest();
        req.setName(" 手动A ");
        req.setType(ViewerTagType.MANUAL);
        when(tagRepository.existsByNameAndDeletedTimeIsNull("手动A")).thenReturn(false);
        ViewerTagGroupEntity g = new ViewerTagGroupEntity();
        g.setId(1L);
        g.setName("默认分组");
        g.setIsDefault(1);
        when(groupRepository.findFirstByIsDefaultAndDeletedTimeIsNull(1)).thenReturn(Optional.of(g));
        when(tagRepository.save(any(ViewerTagEntity.class))).thenAnswer(inv -> {
            ViewerTagEntity t = inv.getArgument(0);
            t.setId(3L);
            t.setCreatedTime(LocalDateTime.of(2026, 10, 27, 12, 0, 0));
            return t;
        });

        ViewerTagItem item = service.create(req);
        assertEquals(3L, item.getId());
        assertEquals("手动A", item.getName());
        assertEquals(ViewerTagType.MANUAL, item.getType());
        assertEquals(1L, item.getGroupId());
    }

    @Test
    void create_rejectsDuplicateName() {
        CreateViewerTagRequest req = new CreateViewerTagRequest();
        req.setName("重复");
        req.setType(ViewerTagType.MANUAL);
        when(tagRepository.existsByNameAndDeletedTimeIsNull("重复")).thenReturn(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(req));
        assertEquals("标签名称已存在", ex.getMessage());
    }

    @Test
    void update_renames() {
        ViewerTagEntity tag = baseTag(5L, "旧名", ViewerTagType.MANUAL, 1L);
        when(tagRepository.findByIdAndDeletedTimeIsNull(5L)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByNameAndDeletedTimeIsNullAndIdNot("新名", 5L)).thenReturn(false);
        ViewerTagGroupEntity g = new ViewerTagGroupEntity();
        g.setId(1L);
        g.setName("默认分组");
        when(groupRepository.findById(1L)).thenReturn(Optional.of(g));
        when(tagRepository.save(any(ViewerTagEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.countByTagId(5L)).thenReturn(2L);

        UpdateViewerTagRequest req = new UpdateViewerTagRequest();
        req.setName("新名");
        ViewerTagItem item = service.update(5L, req);
        assertEquals("新名", item.getName());
        assertEquals(2L, item.getUserCount());
    }

    @Test
    void delete_softDeletesAndClearsMembers() {
        ViewerTagEntity tag = baseTag(7L, "待删", ViewerTagType.MANUAL, 1L);
        when(tagRepository.findByIdAndDeletedTimeIsNull(7L)).thenReturn(Optional.of(tag));
        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(100L);
        op.setUserName("张三");
        when(userRepository.findById(100L)).thenReturn(Optional.of(op));
        when(tagRepository.save(any(ViewerTagEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(7L, service.delete(7L, 100L).getId());
        verify(memberRepository).deleteByTagId(7L);
        assertEquals(100L, tag.getDeletedBy());
        assertEquals("张三", tag.getDeletedByName());
    }

    @Test
    void list_rejectsBadType() {
        assertThrows(IllegalArgumentException.class, () -> service.list(1, 20, 9, null, null));
    }

    @Test
    void createGroup_ok() {
        CreateViewerTagGroupRequest req = new CreateViewerTagGroupRequest();
        req.setName(" 自定义1 ");
        when(groupRepository.existsByNameAndDeletedTimeIsNull("自定义1")).thenReturn(false);
        when(groupRepository.findMaxSortOrderAmongActive()).thenReturn(0);
        when(groupRepository.save(any(ViewerTagGroupEntity.class))).thenAnswer(inv -> {
            ViewerTagGroupEntity g = inv.getArgument(0);
            g.setId(2L);
            return g;
        });
        ViewerTagGroupPageItem item = service.createGroup(req);
        assertEquals(2L, item.getId());
        assertEquals("自定义1", item.getName());
        assertEquals(0L, item.getTagCount());
        assertFalse(item.isDefault());
    }

    @Test
    void deleteGroup_rejectsWhenHasTags() {
        ViewerTagGroupEntity g = new ViewerTagGroupEntity();
        g.setId(2L);
        g.setName("自定义");
        g.setIsDefault(0);
        when(groupRepository.findByIdAndDeletedTimeIsNull(2L)).thenReturn(Optional.of(g));
        when(tagRepository.countByGroupIdAndDeletedTimeIsNull(2L)).thenReturn(1L);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.deleteGroup(2L));
        assertTrue(ex.getMessage().contains("不可删除分组"));
    }

    @Test
    void deleteGroup_rejectsDefault() {
        ViewerTagGroupEntity g = new ViewerTagGroupEntity();
        g.setId(1L);
        g.setName("默认分组");
        g.setIsDefault(1);
        when(groupRepository.findByIdAndDeletedTimeIsNull(1L)).thenReturn(Optional.of(g));
        assertThrows(IllegalArgumentException.class, () -> service.deleteGroup(1L));
    }

    private static ViewerTagEntity baseTag(long id, String name, int type, long groupId) {
        ViewerTagEntity tag = new ViewerTagEntity();
        tag.setId(id);
        tag.setName(name);
        tag.setType(type);
        tag.setGroupId(groupId);
        tag.setCreatedTime(LocalDateTime.of(2026, 10, 27, 12, 0, 0));
        return tag;
    }
}
