package com.zhibo.admin.material;

import com.zhibo.admin.material.dto.CreateMaterialGroupRequest;
import com.zhibo.admin.material.dto.MaterialGroupResponse;
import com.zhibo.admin.material.dto.MaterialListResponse;
import com.zhibo.admin.material.dto.MaterialItemResponse;
import com.zhibo.admin.material.dto.RegisterMaterialsRequest;
import com.zhibo.admin.material.dto.RegisterMaterialsResponse;
import com.zhibo.admin.material.dto.RenameMaterialGroupRequest;
import com.zhibo.admin.material.dto.RenameMaterialRequest;
import com.zhibo.admin.material.dto.UpdateMaterialAuditRequest;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialGroupEntity;
import com.zhibo.material.MaterialGroupRepository;
import com.zhibo.material.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private MaterialGroupRepository groupRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    @InjectMocks
    private MaterialService materialService;

    @Test
    void listGroups_returnsCount() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findByTypeOrderByDefaultGroupDescSortOrderAscCreatedAtAsc("image"))
                .thenReturn(List.of(g));
        when(materialRepository.countByGroupIdAndDeletedAtIsNull("g1")).thenReturn(3L);

        List<MaterialGroupResponse> list = materialService.listGroups("image");
        assertEquals(1, list.size());
        assertEquals("g1", list.get(0).getId());
        assertEquals(3, list.get(0).getCount());
        assertTrue(list.get(0).isDefault());
    }

    @Test
    void createGroup_rejectsDuplicateName() {
        CreateMaterialGroupRequest req = new CreateMaterialGroupRequest();
        req.setType("image");
        req.setName("宣传素材");
        when(groupRepository.existsByTypeAndName("image", "宣传素材")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.createGroup(req));
        assertEquals("同类型下分组名称已存在", ex.getMessage());
    }

    @Test
    void deleteGroup_rejectsDefault() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(g));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.deleteGroup("g1"));
        assertEquals("默认分组不可删除", ex.getMessage());
        verify(groupRepository, never()).delete(any());
    }

    @Test
    void deleteGroup_rejectsWhenHasMaterials() {
        MaterialGroupEntity g = group("g2", "image", "宣传", false);
        when(groupRepository.findById("g2")).thenReturn(Optional.of(g));
        when(materialRepository.countByGroupIdAndDeletedAtIsNull("g2")).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.deleteGroup("g2"));
        assertEquals("分组下仍有素材", ex.getMessage());
    }

    @Test
    void renameGroup_ok() {
        MaterialGroupEntity g = group("g2", "image", "旧名", false);
        when(groupRepository.findById("g2")).thenReturn(Optional.of(g));
        when(groupRepository.existsByTypeAndName("image", "新名")).thenReturn(false);
        when(materialRepository.countByGroupIdAndDeletedAtIsNull("g2")).thenReturn(0L);

        RenameMaterialGroupRequest req = new RenameMaterialGroupRequest();
        req.setName(" 新名 ");
        MaterialGroupResponse resp = materialService.renameGroup("g2", req);
        assertEquals("新名", resp.getName());
        assertEquals("新名", g.getName());
    }

    @Test
    void list_rejectsPageZero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.list("image", null, null, false, 0, 20));
        assertEquals("page 不能小于 1", ex.getMessage());
    }

    @Test
    void list_mapsThumbUrlForImage() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setCreatedAt(LocalDateTime.of(2026, 10, 12, 14, 0, 0));
        when(materialRepository.search(
                        eq("image"), isNull(), isNull(), eq(false), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));

        MaterialListResponse resp = materialService.list("image", null, null, false, 1, 20);
        assertEquals(1, resp.getTotal());
        assertEquals(1, resp.getPage());
        assertEquals("https://cdn/a.jpg", resp.getList().get(0).getThumbUrl());
        assertEquals("2026-10-12 14:00:00", resp.getList().get(0).getUploadedAt());
    }

    @Test
    void register_batchPersists() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(g));
        when(materialRepository.existsByObjectKeyAndDeletedAtIsNull(any())).thenReturn(false);
        when(materialRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        SysUserAccountEntity user = uploader(10001L, "张三",
                "[{\"department_id\":10,\"department_name\":\"技术部\"}]");
        when(userRepository.findById(10001L)).thenReturn(Optional.of(user));

        RegisterMaterialsRequest req = new RegisterMaterialsRequest();
        req.setType("image");
        req.setGroupId("g1");
        RegisterMaterialsRequest.RegisterMaterialItem item =
                new RegisterMaterialsRequest.RegisterMaterialItem();
        item.setName("封面.jpg");
        item.setUrl("https://cdn/cover.jpg");
        item.setObjectKey("material/image/1_封面.jpg");
        item.setFileSize(100L);
        item.setMeta("100*100");
        req.setItems(List.of(item));

        RegisterMaterialsResponse resp = materialService.register(req, 10001L);
        assertEquals(1, resp.getList().size());
        assertEquals("封面.jpg", resp.getList().get(0).getName());
        assertEquals("10001", resp.getList().get(0).getUploaderId());
        assertEquals("张三", resp.getList().get(0).getUploaderName());
        assertEquals("技术部", resp.getList().get(0).getDepartmentName());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(materialRepository).saveAll(captor.capture());
        assertEquals("material/image/1_封面.jpg", captor.getValue().get(0).getObjectKey());
        assertEquals("available", captor.getValue().get(0).getStatus());
        assertEquals("pending", captor.getValue().get(0).getAiAuditStatus());
        assertEquals("pending", captor.getValue().get(0).getRiskAuditStatus());
        assertEquals(10001L, captor.getValue().get(0).getUploaderId());
        assertEquals("张三", captor.getValue().get(0).getUploaderName());
        assertEquals("技术部", captor.getValue().get(0).getDepartmentName());
        assertEquals("pending", resp.getList().get(0).getAiAuditStatus());
        assertEquals("pending", resp.getList().get(0).getRiskAuditStatus());
    }

    @Test
    void updateAiAudit_setsPassed() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setAiAuditStatus("pending");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        UpdateMaterialAuditRequest req = new UpdateMaterialAuditRequest();
        req.setStatus("passed");
        MaterialItemResponse resp = materialService.updateAiAudit("m1", req);

        assertEquals("passed", resp.getAiAuditStatus());
        assertEquals("passed", m.getAiAuditStatus());
        verify(materialRepository).save(m);
    }

    @Test
    void updateRiskAudit_setsRejected() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setRiskAuditStatus("passed");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        UpdateMaterialAuditRequest req = new UpdateMaterialAuditRequest();
        req.setStatus("rejected");
        MaterialItemResponse resp = materialService.updateRiskAudit("m1", req);

        assertEquals("rejected", resp.getRiskAuditStatus());
        assertEquals("rejected", m.getRiskAuditStatus());
    }

    @Test
    void updateAiAudit_rejectsPending() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        UpdateMaterialAuditRequest req = new UpdateMaterialAuditRequest();
        req.setStatus("pending");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.updateAiAudit("m1", req));
        assertEquals("status 须为 passed / rejected", ex.getMessage());
    }

    @Test
    void register_rejectsDuplicateObjectKey() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(g));
        when(materialRepository.existsByObjectKeyAndDeletedAtIsNull("material/image/1.jpg"))
                .thenReturn(true);
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));

        RegisterMaterialsRequest req = new RegisterMaterialsRequest();
        req.setType("image");
        req.setGroupId("g1");
        RegisterMaterialsRequest.RegisterMaterialItem item =
                new RegisterMaterialsRequest.RegisterMaterialItem();
        item.setName("a.jpg");
        item.setUrl("https://cdn/a.jpg");
        item.setObjectKey("material/image/1.jpg");
        req.setItems(List.of(item));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.register(req, 10001L));
        assertTrue(ex.getMessage().contains("objectKey 已存在"));
    }

    @Test
    void register_rejectsMissingUploader() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        RegisterMaterialsRequest req = new RegisterMaterialsRequest();
        req.setType("image");
        req.setGroupId("g1");
        req.setItems(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.register(req, 9L));
        assertEquals("当前员工不存在", ex.getMessage());
    }

    @Test
    void rename_updatesNameOnly() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setObjectKey("material/image/1_a.jpg");
        m.setUrl("https://cdn/a.jpg");
        m.setCreatedAt(LocalDateTime.of(2026, 10, 12, 14, 0, 0));
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        RenameMaterialRequest req = new RenameMaterialRequest();
        req.setName(" 新封面.jpg ");
        MaterialItemResponse resp = materialService.rename("m1", req);

        assertEquals("新封面.jpg", resp.getName());
        assertEquals("新封面.jpg", m.getName());
        assertEquals("material/image/1_a.jpg", m.getObjectKey());
        assertEquals("https://cdn/a.jpg", m.getUrl());
        assertEquals("https://cdn/a.jpg", resp.getThumbUrl());
        verify(materialRepository).save(m);
    }

    @Test
    void rename_rejectsBlankName() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        RenameMaterialRequest req = new RenameMaterialRequest();
        req.setName("   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.rename("m1", req));
        assertEquals("name 不能为空", ex.getMessage());
    }

    @Test
    void rename_rejectsDeleted() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setDeletedAt(LocalDateTime.now());
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        RenameMaterialRequest req = new RenameMaterialRequest();
        req.setName("b.jpg");
        assertThrows(IllegalArgumentException.class, () -> materialService.rename("m1", req));
    }

    @Test
    void softDelete_setsDeletedAt() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        assertEquals("m1", materialService.softDelete("m1").getId());
        assertTrue(m.getDeletedAt() != null);
        verify(materialRepository).save(m);
    }

    @Test
    void softDelete_alreadyDeleted_rejects() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setDeletedAt(LocalDateTime.now());
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        assertThrows(IllegalArgumentException.class, () -> materialService.softDelete("m1"));
    }

    @Test
    void toItem_documentOmitsThumb() {
        MaterialEntity m = material("m1", "document", "g1", "a.pdf");
        m.setCreatedAt(LocalDateTime.now());
        when(materialRepository.search(
                        eq("document"), isNull(), isNull(), eq(false), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));

        MaterialListResponse resp = materialService.list("document", null, null, false, 1, 20);
        assertNull(resp.getList().get(0).getThumbUrl());
        assertFalse(resp.getList().get(0).getUrl().isBlank());
    }

    @Test
    void list_selectableOnly_passesFlagToRepository() {
        when(materialRepository.search(
                        eq("image"), isNull(), isNull(), eq(true), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        MaterialListResponse resp = materialService.list("image", null, null, true, 1, 20);
        assertEquals(0, resp.getTotal());
        verify(materialRepository).search(
                eq("image"), isNull(), isNull(), eq(true), eq(PageRequest.of(0, 20)));
    }

    private static MaterialGroupEntity group(String id, String type, String name, boolean def) {
        MaterialGroupEntity g = new MaterialGroupEntity();
        g.setId(id);
        g.setType(type);
        g.setName(name);
        g.setDefaultGroup(def);
        g.setSortOrder(0);
        return g;
    }

    private static MaterialEntity material(String id, String type, String groupId, String name) {
        MaterialEntity m = new MaterialEntity();
        m.setId(id);
        m.setType(type);
        m.setGroupId(groupId);
        m.setName(name);
        m.setUrl("https://cdn/" + name);
        m.setObjectKey("material/" + type + "/1_" + name);
        m.setStatus("available");
        m.setAiAuditStatus("pending");
        m.setRiskAuditStatus("pending");
        return m;
    }

    private static SysUserAccountEntity uploader(long id, String name, String qwBmxx) {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(id);
        user.setUserName(name);
        user.setUserAccount("acc" + id);
        user.setRsStatus(1);
        user.setPassword("x");
        user.setQwBmxx(qwBmxx);
        return user;
    }
}
