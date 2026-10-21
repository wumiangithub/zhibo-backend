package com.zhibo.admin.material;

import com.zhibo.admin.material.dto.ArticleDetailResponse;
import com.zhibo.admin.material.dto.CreateArticleRequest;
import com.zhibo.admin.material.dto.MoveMaterialRequest;
import com.zhibo.admin.material.dto.ReplaceImageRequest;
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
import com.zhibo.material.MaterialArticleEntity;
import com.zhibo.material.MaterialArticleRepository;
import com.zhibo.material.MaterialAuditLogRepository;
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
    private MaterialArticleRepository articleRepository;
    @Mock
    private MaterialAuditLogRepository auditLogRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    @InjectMocks
    private MaterialService materialService;

    @Test
    void listGroups_returnsCount() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findByTypeOrderByDefaultGroupDescSortOrderAscCreatedTimeAsc("image"))
                .thenReturn(List.of(g));
        when(materialRepository.countByGroupIdAndDeletedTimeIsNull("g1")).thenReturn(3L);

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
        when(materialRepository.countByGroupIdAndDeletedTimeIsNull("g2")).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.deleteGroup("g2"));
        assertEquals("分组下仍有素材", ex.getMessage());
    }

    @Test
    void renameGroup_ok() {
        MaterialGroupEntity g = group("g2", "image", "旧名", false);
        when(groupRepository.findById("g2")).thenReturn(Optional.of(g));
        when(groupRepository.existsByTypeAndName("image", "新名")).thenReturn(false);
        when(materialRepository.countByGroupIdAndDeletedTimeIsNull("g2")).thenReturn(0L);

        RenameMaterialGroupRequest req = new RenameMaterialGroupRequest();
        req.setName(" 新名 ");
        MaterialGroupResponse resp = materialService.renameGroup("g2", req);
        assertEquals("新名", resp.getName());
        assertEquals("新名", g.getName());
    }

    @Test
    void list_rejectsPageZero() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.list("image", null, null, null, false, 0, 20));
        assertEquals("page 不能小于 1", ex.getMessage());
    }

    @Test
    void list_mapsThumbUrlForImage() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setCreatedTime(LocalDateTime.of(2026, 10, 12, 14, 0, 0));
        when(materialRepository.search(
                        eq("image"), isNull(), isNull(), isNull(), eq(false), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));

        MaterialListResponse resp = materialService.list("image", null, null, null, false, 1, 20);
        assertEquals(1, resp.getTotal());
        assertEquals(1, resp.getPage());
        assertEquals("https://cdn/a.jpg", resp.getList().get(0).getThumbUrl());
        assertEquals("2026-10-12 14:00:00", resp.getList().get(0).getUploadedTime());
    }

    @Test
    void register_batchPersists() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(g));
        when(materialRepository.existsByObjectKeyAndDeletedTimeIsNull(any())).thenReturn(false);
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
        assertEquals(10001L, resp.getList().get(0).getUploaderId());
        assertEquals("张三", resp.getList().get(0).getUploaderName());
        assertEquals("技术部", resp.getList().get(0).getDepartmentName());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(materialRepository).saveAll(captor.capture());
        assertEquals("material/image/1_封面.jpg", captor.getValue().get(0).getObjectKey());
        assertEquals("available", captor.getValue().get(0).getStatus());
        assertEquals(0, captor.getValue().get(0).getAiAuditStatus());
        assertEquals(0, captor.getValue().get(0).getRiskAuditStatus());
        assertEquals(10001L, captor.getValue().get(0).getUploaderId());
        assertEquals("张三", captor.getValue().get(0).getUploaderName());
        assertEquals("技术部", captor.getValue().get(0).getDepartmentName());
        assertEquals(0, resp.getList().get(0).getAiAuditStatus());
        assertEquals(0, resp.getList().get(0).getRiskAuditStatus());
    }

    @Test
    void updateAiAudit_setsPassed() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setAiAuditStatus(0);
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));

        UpdateMaterialAuditRequest req = new UpdateMaterialAuditRequest();
        req.setStatus(1);
        MaterialItemResponse resp = materialService.updateAiAudit("m1", req, 10001L);

        assertEquals(1, resp.getAiAuditStatus());
        assertEquals(1, m.getAiAuditStatus());
        verify(materialRepository).save(m);
        verify(auditLogRepository).save(any());
    }

    @Test
    void updateRiskAudit_setsRejected() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setRiskAuditStatus(1);
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));

        UpdateMaterialAuditRequest req = new UpdateMaterialAuditRequest();
        req.setStatus(2);
        MaterialItemResponse resp = materialService.updateRiskAudit("m1", req, 10001L);

        assertEquals(2, resp.getRiskAuditStatus());
        assertEquals(2, m.getRiskAuditStatus());
        verify(auditLogRepository).save(any());
    }

    @Test
    void updateAiAudit_rejectsPending() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        UpdateMaterialAuditRequest req = new UpdateMaterialAuditRequest();
        req.setStatus(0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.updateAiAudit("m1", req, 10001L));
        assertEquals("status 须为 1 或 2", ex.getMessage());
    }

    @Test
    void listAudits_synthesizesSubmitWhenMissing() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setUploaderId(10001L);
        m.setUploaderName("张三");
        m.setDepartmentName("研究部");
        m.setCreatedTime(LocalDateTime.of(2026, 10, 20, 14, 20, 0));
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        when(auditLogRepository.findByMaterialIdOrderByCreatedTimeDesc("m1")).thenReturn(List.of());

        var resp = materialService.listAudits("m1");
        assertEquals(1, resp.getList().size());
        assertEquals("submit", resp.getList().get(0).getAction());
        assertEquals("张三", resp.getList().get(0).getOperatorName());
        assertEquals("研究部", resp.getList().get(0).getDepartmentName());
        assertNull(resp.getList().get(0).getResult());
    }

    @Test
    void register_rejectsDuplicateObjectKey() {
        MaterialGroupEntity g = group("g1", "image", "默认分组", true);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(g));
        when(materialRepository.existsByObjectKeyAndDeletedTimeIsNull("material/image/1.jpg"))
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
        m.setCreatedTime(LocalDateTime.of(2026, 10, 12, 14, 0, 0));
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
        m.setDeletedTime(LocalDateTime.now());
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        RenameMaterialRequest req = new RenameMaterialRequest();
        req.setName("b.jpg");
        assertThrows(IllegalArgumentException.class, () -> materialService.rename("m1", req));
    }

    @Test
    void softDelete_setsDeletedTime() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        assertEquals("m1", materialService.softDelete("m1").getId());
        assertTrue(m.getDeletedTime() != null);
        verify(materialRepository).save(m);
    }

    @Test
    void softDelete_alreadyDeleted_rejects() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setDeletedTime(LocalDateTime.now());
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));

        assertThrows(IllegalArgumentException.class, () -> materialService.softDelete("m1"));
    }

    @Test
    void createArticle_writesTitleAuthorAndSnapshot() {
        MaterialGroupEntity g = group("g-article", "article", "默认分组", true);
        when(groupRepository.findById("g-article")).thenReturn(Optional.of(g));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));
        when(materialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateArticleRequest req = new CreateArticleRequest();
        req.setGroupId("g-article");
        req.setTitle("  这里是文章的标题  ");
        req.setAuthor(" 作者 ");
        req.setContent("<p>正文</p>");

        MaterialItemResponse resp = materialService.createArticle(req, 10001L);

        assertEquals("这里是文章的标题", resp.getName());
        assertEquals("article", resp.getType());
        assertEquals("g-article", resp.getGroupId());
        assertEquals(0, resp.getAiAuditStatus());
        assertEquals(0, resp.getRiskAuditStatus());
        assertEquals(10001L, resp.getUploaderId());
        assertEquals("张三", resp.getUploaderName());
        assertNull(resp.getUrl());
        assertNull(resp.getObjectKey());
        assertNull(resp.getThumbUrl());

        ArgumentCaptor<MaterialArticleEntity> body = ArgumentCaptor.forClass(MaterialArticleEntity.class);
        verify(articleRepository).save(body.capture());
        assertEquals("作者", body.getValue().getAuthor());
        assertEquals("<p>正文</p>", body.getValue().getContent());
        assertEquals(resp.getId(), body.getValue().getId());
    }

    @Test
    void createArticle_rejectsEmptyHtml() {
        MaterialGroupEntity g = group("g-article", "article", "默认分组", true);
        when(groupRepository.findById("g-article")).thenReturn(Optional.of(g));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));

        CreateArticleRequest req = new CreateArticleRequest();
        req.setGroupId("g-article");
        req.setTitle("标题");
        req.setAuthor("作者");
        req.setContent("<p><br></p>");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.createArticle(req, 10001L));
        assertEquals("content 不能为空", ex.getMessage());
        verify(materialRepository, never()).saveAndFlush(any());
    }

    @Test
    void createArticle_rejectsLongTitleAndWrongGroup() {
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));
        MaterialGroupEntity image = group("g1", "image", "默认分组", true);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(image));

        CreateArticleRequest wrongGroup = new CreateArticleRequest();
        wrongGroup.setGroupId("g1");
        wrongGroup.setTitle("标题");
        wrongGroup.setAuthor("作者");
        wrongGroup.setContent("<p>正文</p>");
        IllegalArgumentException groupEx = assertThrows(IllegalArgumentException.class,
                () -> materialService.createArticle(wrongGroup, 10001L));
        assertEquals("分组类型与素材类型不一致", groupEx.getMessage());

        MaterialGroupEntity article = group("g-article", "article", "默认分组", true);
        when(groupRepository.findById("g-article")).thenReturn(Optional.of(article));
        CreateArticleRequest longTitle = new CreateArticleRequest();
        longTitle.setGroupId("g-article");
        longTitle.setTitle("标".repeat(46));
        longTitle.setAuthor("作者");
        longTitle.setContent("<p>正文</p>");
        IllegalArgumentException titleEx = assertThrows(IllegalArgumentException.class,
                () -> materialService.createArticle(longTitle, 10001L));
        assertEquals("title 不能超过45个字", titleEx.getMessage());
    }

    @Test
    void register_rejectsArticleType() {
        when(userRepository.findById(10001L)).thenReturn(Optional.of(uploader(10001L, "张三", null)));
        RegisterMaterialsRequest req = new RegisterMaterialsRequest();
        req.setType("article");
        req.setGroupId("g1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.register(req, 10001L));
        assertEquals("type 须为 image / video / document", ex.getMessage());
    }

    @Test
    void list_acceptsArticleType() {
        when(materialRepository.search(
                        eq("article"), isNull(), isNull(), isNull(), eq(false), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        MaterialListResponse resp = materialService.list("article", null, null, null, false, 1, 20);
        assertEquals(0, resp.getTotal());
    }

    @Test
    void move_changesGroupWithoutTouchingAudit() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setAiAuditStatus(1);
        m.setRiskAuditStatus(2);
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        when(groupRepository.findById("g2")).thenReturn(Optional.of(group("g2", "image", "分组2", false)));

        MoveMaterialRequest req = new MoveMaterialRequest();
        req.setGroupId("g2");
        MaterialItemResponse resp = materialService.move("m1", req);

        assertEquals("g2", resp.getGroupId());
        assertEquals(1, resp.getAiAuditStatus());
        assertEquals(2, resp.getRiskAuditStatus());
    }

    @Test
    void move_rejectsDifferentType() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        when(groupRepository.findById("g-article")).thenReturn(
                Optional.of(group("g-article", "article", "默认分组", true)));

        MoveMaterialRequest req = new MoveMaterialRequest();
        req.setGroupId("g-article");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.move("m1", req));
        assertEquals("分组类型与素材类型不一致", ex.getMessage());
    }

    @Test
    void replaceImage_pendingRejects() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        ReplaceImageRequest req = new ReplaceImageRequest();
        req.setUrl("https://cdn/b.jpg");
        req.setObjectKey("material/image/2_b.jpg");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.replaceImage("m1", req));
        assertEquals("审核中不可编辑", ex.getMessage());
    }

    @Test
    void replaceImage_passedResetsAudit() {
        MaterialEntity m = material("m1", "image", "g1", "a.jpg");
        m.setAiAuditStatus(1);
        m.setRiskAuditStatus(1);
        when(materialRepository.findById("m1")).thenReturn(Optional.of(m));
        when(materialRepository.existsByObjectKeyAndDeletedTimeIsNull("material/image/2_b.jpg"))
                .thenReturn(false);

        ReplaceImageRequest req = new ReplaceImageRequest();
        req.setUrl("https://cdn/b.jpg");
        req.setObjectKey("material/image/2_b.jpg");
        req.setFileSize(12L);
        req.setMeta("100*80");
        MaterialItemResponse resp = materialService.replaceImage("m1", req);

        assertEquals("https://cdn/b.jpg", resp.getUrl());
        assertEquals(0, resp.getAiAuditStatus());
        assertEquals(0, resp.getRiskAuditStatus());
        assertEquals("100*80", resp.getMeta());
    }

    @Test
    void updateArticle_pendingRejects() {
        MaterialEntity m = material("a1", "article", "g-article", "标题");
        when(materialRepository.findById("a1")).thenReturn(Optional.of(m));
        CreateArticleRequest req = new CreateArticleRequest();
        req.setTitle("新标题");
        req.setAuthor("作者");
        req.setContent("<p>新正文</p>");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.updateArticle("a1", req));
        assertEquals("审核中不可编辑", ex.getMessage());
    }

    @Test
    void updateArticle_passedResetsAudit() {
        MaterialEntity m = material("a1", "article", "g-article", "标题");
        m.setAiAuditStatus(1);
        m.setRiskAuditStatus(2);
        MaterialArticleEntity body = new MaterialArticleEntity();
        body.setId("a1");
        body.setAuthor("旧作者");
        body.setContent("<p>旧</p>");
        when(materialRepository.findById("a1")).thenReturn(Optional.of(m));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(body));

        CreateArticleRequest req = new CreateArticleRequest();
        req.setTitle("新标题");
        req.setAuthor("新作者");
        req.setContent("<p>新正文</p>");
        MaterialItemResponse resp = materialService.updateArticle("a1", req);

        assertEquals("新标题", resp.getName());
        assertEquals(0, resp.getAiAuditStatus());
        assertEquals(0, resp.getRiskAuditStatus());
        assertEquals("新作者", body.getAuthor());
        assertEquals("<p>新正文</p>", body.getContent());
    }

    @Test
    void getArticle_returnsBody() {
        MaterialEntity m = material("a1", "article", "g-article", "标题");
        m.setAiAuditStatus(1);
        m.setRiskAuditStatus(1);
        MaterialArticleEntity body = new MaterialArticleEntity();
        body.setId("a1");
        body.setAuthor("作者");
        body.setContent("<p>正文</p>");
        when(materialRepository.findById("a1")).thenReturn(Optional.of(m));
        when(articleRepository.findById("a1")).thenReturn(Optional.of(body));

        ArticleDetailResponse detail = materialService.getArticle("a1");
        assertEquals("标题", detail.getTitle());
        assertEquals("作者", detail.getAuthor());
        assertEquals("<p>正文</p>", detail.getContent());
        assertEquals(1, detail.getAiAuditStatus());
    }

    @Test
    void toItem_documentOmitsThumb() {
        MaterialEntity m = material("m1", "document", "g1", "a.pdf");
        m.setCreatedTime(LocalDateTime.now());
        when(materialRepository.search(
                        eq("document"), isNull(), isNull(), isNull(), eq(false), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));

        MaterialListResponse resp = materialService.list("document", null, null, null, false, 1, 20);
        assertNull(resp.getList().get(0).getThumbUrl());
        assertFalse(resp.getList().get(0).getUrl().isBlank());
    }

    @Test
    void list_selectableOnly_passesFlagToRepository() {
        when(materialRepository.search(
                        eq("image"), isNull(), isNull(), isNull(), eq(true), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        MaterialListResponse resp = materialService.list("image", null, null, null, true, 1, 20);
        assertEquals(0, resp.getTotal());
        verify(materialRepository).search(
                eq("image"), isNull(), isNull(), isNull(), eq(true), eq(PageRequest.of(0, 20)));
    }

    @Test
    void list_passesKeywordAndAuditStatus() {
        when(materialRepository.search(
                        eq("image"), isNull(), eq("封面"), eq(0), eq(false), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        materialService.list("image", "  ", " 封面 ", " 0 ", false, 1, 20);

        verify(materialRepository).search(
                eq("image"), isNull(), eq("封面"), eq(0), eq(false), eq(PageRequest.of(0, 20)));
    }

    @Test
    void list_rejectsUnknownAuditStatus() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> materialService.list("image", null, null, "approved", false, 1, 20));
        assertEquals("auditStatus 仅支持 0、1、2", ex.getMessage());
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
        m.setAiAuditStatus(0);
        m.setRiskAuditStatus(0);
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
