package com.zhibo.admin.material;

import com.zhibo.admin.material.dto.CreateMaterialGroupRequest;
import com.zhibo.admin.material.dto.DeleteIdResponse;
import com.zhibo.admin.material.dto.MaterialGroupResponse;
import com.zhibo.admin.material.dto.MaterialItemResponse;
import com.zhibo.admin.material.dto.MaterialListResponse;
import com.zhibo.admin.material.dto.RegisterMaterialsRequest;
import com.zhibo.admin.material.dto.RegisterMaterialsResponse;
import com.zhibo.admin.material.dto.RenameMaterialGroupRequest;
import com.zhibo.admin.material.dto.RenameMaterialRequest;
import com.zhibo.admin.material.dto.UpdateMaterialAuditRequest;
import com.zhibo.admin.auth.AuthService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialGroupEntity;
import com.zhibo.material.MaterialGroupRepository;
import com.zhibo.material.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private static final Set<String> TYPES = Set.of("image", "video", "document");
    private static final Set<String> AUDIT_WRITE_STATUSES = Set.of("passed", "rejected");
    private static final DateTimeFormatter UPLOADED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_BATCH = 100;

    private final MaterialGroupRepository groupRepository;
    private final MaterialRepository materialRepository;
    private final SysUserAccountRepository userRepository;

    public List<MaterialGroupResponse> listGroups(String type) {
        String t = requireType(type);
        return groupRepository.findByTypeOrderByDefaultGroupDescSortOrderAscCreatedAtAsc(t)
                .stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Transactional
    public MaterialGroupResponse createGroup(CreateMaterialGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String type = requireType(request.getType());
        String name = requireGroupName(request.getName());
        if (groupRepository.existsByTypeAndName(type, name)) {
            throw new IllegalArgumentException("同类型下分组名称已存在");
        }
        MaterialGroupEntity entity = new MaterialGroupEntity();
        entity.setType(type);
        entity.setName(name);
        entity.setDefaultGroup(false);
        entity.setSortOrder(nextSortOrder(type));
        groupRepository.save(entity);
        return toGroupResponse(entity);
    }

    @Transactional
    public MaterialGroupResponse renameGroup(String id, RenameMaterialGroupRequest request) {
        MaterialGroupEntity entity = requireGroup(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = requireGroupName(request.getName());
        if (!name.equals(entity.getName())
                && groupRepository.existsByTypeAndName(entity.getType(), name)) {
            throw new IllegalArgumentException("同类型下分组名称已存在");
        }
        entity.setName(name);
        groupRepository.save(entity);
        return toGroupResponse(entity);
    }

    @Transactional
    public DeleteIdResponse deleteGroup(String id) {
        MaterialGroupEntity entity = requireGroup(id);
        if (entity.isDefaultGroup()) {
            throw new IllegalArgumentException("默认分组不可删除");
        }
        long count = materialRepository.countByGroupIdAndDeletedAtIsNull(entity.getId());
        if (count > 0) {
            throw new IllegalArgumentException("分组下仍有素材");
        }
        groupRepository.delete(entity);
        return new DeleteIdResponse(entity.getId());
    }

    /**
     * @param selectableOnly true：仅返回 AI、风控两审都为 passed 的素材（封面/课件选择弹窗）；
     *                       false：素材中心主列表，不按审核过滤。total/list 均按过滤后分页。
     */
    public MaterialListResponse list(
            String type,
            String groupId,
            String keyword,
            boolean selectableOnly,
            int page,
            int pageSize) {
        String t = requireType(type);
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        String groupFilter = blankToNull(groupId);
        if (groupFilter != null) {
            MaterialGroupEntity group = requireGroup(groupFilter);
            if (!t.equals(group.getType())) {
                throw new IllegalArgumentException("分组类型与素材类型不一致");
            }
        }
        String keywordFilter = blankToNull(keyword);

        Page<MaterialEntity> result = materialRepository.search(
                t, groupFilter, keywordFilter, selectableOnly, PageRequest.of(page - 1, pageSize));

        MaterialListResponse response = new MaterialListResponse();
        response.setTotal(result.getTotalElements());
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setList(result.getContent().stream().map(this::toItemResponse).toList());
        return response;
    }

    @Transactional
    public RegisterMaterialsResponse register(RegisterMaterialsRequest request, long uploaderId) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        SysUserAccountEntity uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
        String uploaderName = blankToNull(uploader.getUserName());
        String departmentName = AuthService.resolveDepartmentName(uploader);

        String type = requireType(request.getType());
        String groupId = blankToNull(request.getGroupId());
        if (groupId == null) {
            throw new IllegalArgumentException("groupId 不能为空");
        }
        MaterialGroupEntity group = requireGroup(groupId);
        if (!type.equals(group.getType())) {
            throw new IllegalArgumentException("分组类型与素材类型不一致");
        }
        List<RegisterMaterialsRequest.RegisterMaterialItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
        if (items.size() > MAX_BATCH) {
            throw new IllegalArgumentException("items 不能超过" + MAX_BATCH + "条");
        }

        Set<String> keysInBatch = new HashSet<>();
        List<MaterialEntity> entities = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            RegisterMaterialsRequest.RegisterMaterialItem item = items.get(i);
            if (item == null) {
                throw new IllegalArgumentException("items[" + i + "] 不能为空");
            }
            String name = requireTrimmed(item.getName(), "items[" + i + "].name", 256);
            String url = requireTrimmed(item.getUrl(), "items[" + i + "].url", 2048);
            String objectKey = requireTrimmed(item.getObjectKey(), "items[" + i + "].objectKey", 1024);
            if (!keysInBatch.add(objectKey)) {
                throw new IllegalArgumentException("items 内 objectKey 重复");
            }
            if (materialRepository.existsByObjectKeyAndDeletedAtIsNull(objectKey)) {
                throw new IllegalArgumentException("objectKey 已存在: " + objectKey);
            }
            Long fileSize = item.getFileSize();
            if (fileSize != null && fileSize < 0) {
                throw new IllegalArgumentException("items[" + i + "].fileSize 不能为负");
            }
            String mimeType = optionalTrimmed(item.getMimeType(), "items[" + i + "].mimeType", 128);
            String meta = optionalTrimmed(item.getMeta(), "items[" + i + "].meta", 128);

            MaterialEntity entity = new MaterialEntity();
            entity.setType(type);
            entity.setGroupId(groupId);
            entity.setName(name);
            entity.setUrl(url);
            entity.setObjectKey(objectKey);
            entity.setFileSize(fileSize);
            entity.setMimeType(mimeType);
            entity.setMeta(meta);
            entity.setStatus("available");
            entity.setAiAuditStatus("pending");
            entity.setRiskAuditStatus("pending");
            entity.setUploaderId(uploader.getId());
            entity.setUploaderName(uploaderName);
            entity.setDepartmentName(departmentName);
            entities.add(entity);
        }

        materialRepository.saveAll(entities);
        return new RegisterMaterialsResponse(
                entities.stream().map(this::toItemResponse).toList());
    }

    @Transactional
    public MaterialItemResponse rename(String id, RenameMaterialRequest request) {
        MaterialEntity entity = requireActiveMaterial(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        // 只改展示名；不碰 objectKey / url（TOS 对象不变）
        entity.setName(requireTrimmed(request.getName(), "name", 256));
        materialRepository.save(entity);
        return toItemResponse(entity);
    }

    @Transactional
    public MaterialItemResponse updateAiAudit(String id, UpdateMaterialAuditRequest request) {
        MaterialEntity entity = requireActiveMaterial(id);
        entity.setAiAuditStatus(requireAuditWriteStatus(request));
        materialRepository.save(entity);
        return toItemResponse(entity);
    }

    @Transactional
    public MaterialItemResponse updateRiskAudit(String id, UpdateMaterialAuditRequest request) {
        MaterialEntity entity = requireActiveMaterial(id);
        entity.setRiskAuditStatus(requireAuditWriteStatus(request));
        materialRepository.save(entity);
        return toItemResponse(entity);
    }

    @Transactional
    public DeleteIdResponse softDelete(String id) {
        MaterialEntity entity = requireActiveMaterial(id);
        entity.setDeletedAt(LocalDateTime.now());
        materialRepository.save(entity);
        return new DeleteIdResponse(entity.getId());
    }

    private MaterialEntity requireActiveMaterial(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("素材不存在或已删除");
        }
        MaterialEntity entity = materialRepository.findById(id.trim())
                .orElseThrow(() -> new IllegalArgumentException("素材不存在或已删除"));
        if (entity.getDeletedAt() != null) {
            throw new IllegalArgumentException("素材不存在或已删除");
        }
        return entity;
    }

    private MaterialGroupResponse toGroupResponse(MaterialGroupEntity entity) {
        long count = materialRepository.countByGroupIdAndDeletedAtIsNull(entity.getId());
        return new MaterialGroupResponse(
                entity.getId(), entity.getName(), count, entity.isDefaultGroup());
    }

    private MaterialItemResponse toItemResponse(MaterialEntity entity) {
        MaterialItemResponse item = new MaterialItemResponse();
        item.setId(entity.getId());
        item.setType(entity.getType());
        item.setGroupId(entity.getGroupId());
        item.setName(entity.getName());
        item.setUrl(entity.getUrl());
        item.setObjectKey(entity.getObjectKey());
        if (!"document".equals(entity.getType())) {
            item.setThumbUrl(entity.getUrl());
        }
        item.setMeta(entity.getMeta());
        item.setFileSize(entity.getFileSize());
        item.setStatus(entity.getStatus());
        item.setAiAuditStatus(
                entity.getAiAuditStatus() == null || entity.getAiAuditStatus().isBlank()
                        ? "pending"
                        : entity.getAiAuditStatus());
        item.setRiskAuditStatus(
                entity.getRiskAuditStatus() == null || entity.getRiskAuditStatus().isBlank()
                        ? "pending"
                        : entity.getRiskAuditStatus());
        if (entity.getUploaderId() != null) {
            item.setUploaderId(String.valueOf(entity.getUploaderId()));
        }
        item.setUploaderName(blankToNull(entity.getUploaderName()));
        item.setDepartmentName(blankToNull(entity.getDepartmentName()));
        if (entity.getCreatedAt() != null) {
            item.setUploadedAt(entity.getCreatedAt().format(UPLOADED_AT));
        }
        return item;
    }

    private static String requireAuditWriteStatus(UpdateMaterialAuditRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String status = request.getStatus() == null
                ? ""
                : request.getStatus().trim().toLowerCase(Locale.ROOT);
        if (!AUDIT_WRITE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status 须为 passed / rejected");
        }
        return status;
    }

    private MaterialGroupEntity requireGroup(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("分组不存在");
        }
        return groupRepository.findById(id.trim())
                .orElseThrow(() -> new IllegalArgumentException("分组不存在"));
    }

    private int nextSortOrder(String type) {
        return groupRepository.findByTypeOrderByDefaultGroupDescSortOrderAscCreatedAtAsc(type)
                .stream()
                .mapToInt(MaterialGroupEntity::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private static String requireType(String type) {
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw new IllegalArgumentException("type 须为 image / video / document");
        }
        return t;
    }

    private static String requireGroupName(String name) {
        return requireTrimmed(name, "name", 64);
    }

    private static String requireTrimmed(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " 不能超过" + max + "个字");
        }
        return trimmed;
    }

    private static String optionalTrimmed(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " 不能超过" + max + "个字");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
