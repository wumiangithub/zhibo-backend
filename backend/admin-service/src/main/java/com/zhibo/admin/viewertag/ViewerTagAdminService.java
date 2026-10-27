package com.zhibo.admin.viewertag;

import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.viewertag.dto.CreateViewerTagGroupRequest;
import com.zhibo.admin.viewertag.dto.CreateViewerTagRequest;
import com.zhibo.admin.viewertag.dto.DeleteViewerTagGroupResponse;
import com.zhibo.admin.viewertag.dto.DeleteViewerTagResponse;
import com.zhibo.admin.viewertag.dto.UpdateViewerTagGroupRequest;
import com.zhibo.admin.viewertag.dto.UpdateViewerTagRequest;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupItem;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupPageItem;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupPageResponse;
import com.zhibo.admin.viewertag.dto.ViewerTagItem;
import com.zhibo.admin.viewertag.dto.ViewerTagListResponse;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagGroupEntity;
import com.zhibo.viewer.ViewerTagGroupRepository;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import com.zhibo.viewer.ViewerTagType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 顶栏用户管理 · 标签（D80）。 */
@Service
@RequiredArgsConstructor
public class ViewerTagAdminService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ViewerTagRepository tagRepository;
    private final ViewerTagGroupRepository groupRepository;
    private final ViewerTagMemberRepository memberRepository;
    private final SysUserAccountRepository userRepository;

    @Transactional(readOnly = true)
    public List<ViewerTagGroupItem> listGroups() {
        return groupRepository.findByDeletedTimeIsNullOrderBySortOrderAscIdAsc().stream()
                .map(g -> new ViewerTagGroupItem(g.getId(), g.getName(), g.getIsDefault() == 1))
                .toList();
    }

    @Transactional(readOnly = true)
    public ViewerTagGroupPageResponse pageGroups(int page, int pageSize, String name) {
        requirePage(page, pageSize);
        Specification<ViewerTagGroupEntity> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isNull(root.get("deletedTime")));
            if (name != null && !name.isBlank()) {
                preds.add(cb.like(root.get("name"), "%" + name.trim() + "%"));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
        Page<ViewerTagGroupEntity> result = groupRepository.findAll(
                spec,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "sortOrder", "id")));
        Map<Long, Long> tagCounts = loadTagCounts(result.getContent());
        ViewerTagGroupPageResponse response = new ViewerTagGroupPageResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream()
                .map(g -> toGroupPageItem(g, tagCounts.getOrDefault(g.getId(), 0L)))
                .toList());
        return response;
    }

    @Transactional
    public ViewerTagGroupPageItem createGroup(CreateViewerTagGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = requireGroupName(request.getName());
        if (groupRepository.existsByNameAndDeletedTimeIsNull(name)) {
            throw new IllegalArgumentException("分组名称已存在");
        }
        ViewerTagGroupEntity group = new ViewerTagGroupEntity();
        group.setName(name);
        group.setIsDefault(0);
        group.setSortOrder(groupRepository.findMaxSortOrderAmongActive() + 1);
        groupRepository.save(group);
        return toGroupPageItem(group, 0L);
    }

    @Transactional
    public ViewerTagGroupPageItem updateGroup(long id, UpdateViewerTagGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ViewerTagGroupEntity group = requireGroup(id);
        if (group.getIsDefault() == 1) {
            throw new IllegalArgumentException("默认分组不可编辑");
        }
        String name = requireGroupName(request.getName());
        if (groupRepository.existsByNameAndDeletedTimeIsNullAndIdNot(name, id)) {
            throw new IllegalArgumentException("分组名称已存在");
        }
        group.setName(name);
        groupRepository.save(group);
        long tagCount = tagRepository.countByGroupIdAndDeletedTimeIsNull(group.getId());
        return toGroupPageItem(group, tagCount);
    }

    @Transactional
    public DeleteViewerTagGroupResponse deleteGroup(long id) {
        ViewerTagGroupEntity group = requireGroup(id);
        if (group.getIsDefault() == 1) {
            throw new IllegalArgumentException("默认分组不可删除");
        }
        long tagCount = tagRepository.countByGroupIdAndDeletedTimeIsNull(group.getId());
        if (tagCount > 0) {
            throw new IllegalArgumentException("该分组有" + tagCount + "个标签，不可删除分组");
        }
        group.setDeletedTime(LocalDateTime.now());
        groupRepository.save(group);
        return new DeleteViewerTagGroupResponse(group.getId());
    }

    @Transactional(readOnly = true)
    public ViewerTagListResponse list(int page, int pageSize, Integer type, String name, Long groupId) {
        requirePage(page, pageSize);
        if (type != null && !ViewerTagType.isValid(type)) {
            throw new IllegalArgumentException("type 仅支持 1 或 2");
        }

        Specification<ViewerTagEntity> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isNull(root.get("deletedTime")));
            if (type != null) {
                preds.add(cb.equal(root.get("type"), type));
            }
            if (name != null && !name.isBlank()) {
                preds.add(cb.like(root.get("name"), "%" + name.trim() + "%"));
            }
            if (groupId != null) {
                preds.add(cb.equal(root.get("groupId"), groupId));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };

        Page<ViewerTagEntity> result = tagRepository.findAll(
                spec, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdTime")));

        Map<Long, String> groupNames = loadGroupNames(result.getContent());
        Map<Long, Long> counts = loadUserCounts(result.getContent());

        ViewerTagListResponse response = new ViewerTagListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream()
                .map(t -> toItem(t, groupNames.get(t.getGroupId()), counts.getOrDefault(t.getId(), 0L)))
                .toList());
        return response;
    }

    @Transactional(readOnly = true)
    public ViewerTagItem detail(long id) {
        ViewerTagEntity tag = requireActiveTag(id);
        String groupName = groupRepository
                .findById(tag.getGroupId())
                .map(ViewerTagGroupEntity::getName)
                .orElse("");
        long userCount = memberRepository.countByTagId(tag.getId());
        return toItem(tag, groupName, userCount);
    }

    @Transactional
    public ViewerTagItem create(CreateViewerTagRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getType() == null || request.getType() != ViewerTagType.MANUAL) {
            throw new IllegalArgumentException("本轮不支持创建自动标签");
        }
        String name = requireName(request.getName());
        if (tagRepository.existsByNameAndDeletedTimeIsNull(name)) {
            throw new IllegalArgumentException("标签名称已存在");
        }
        ViewerTagGroupEntity group = resolveGroup(request.getGroupId());

        ViewerTagEntity tag = new ViewerTagEntity();
        tag.setName(name);
        tag.setType(ViewerTagType.MANUAL);
        tag.setGroupId(group.getId());
        tagRepository.save(tag);
        return toItem(tag, group.getName(), 0L);
    }

    @Transactional
    public ViewerTagItem update(long id, UpdateViewerTagRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        ViewerTagEntity tag = requireActiveTag(id);
        String name = requireName(request.getName());
        if (tagRepository.existsByNameAndDeletedTimeIsNullAndIdNot(name, id)) {
            throw new IllegalArgumentException("标签名称已存在");
        }
        ViewerTagGroupEntity group;
        if (request.getGroupId() != null) {
            group = requireGroup(request.getGroupId());
            tag.setGroupId(group.getId());
        } else {
            group = groupRepository
                    .findById(tag.getGroupId())
                    .orElseThrow(() -> new IllegalArgumentException("所属分组不存在"));
        }
        tag.setName(name);
        tagRepository.save(tag);
        long userCount = memberRepository.countByTagId(tag.getId());
        return toItem(tag, group.getName(), userCount);
    }

    @Transactional
    public DeleteViewerTagResponse delete(long id, long operatorId) {
        ViewerTagEntity tag = requireActiveTag(id);
        SysUserAccountEntity operator = userRepository
                .findById(operatorId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
        memberRepository.deleteByTagId(tag.getId());
        tag.setDeletedTime(LocalDateTime.now());
        tag.setDeletedBy(operator.getId());
        tag.setDeletedByName(operator.getUserName() == null ? "" : operator.getUserName());
        tagRepository.save(tag);
        return new DeleteViewerTagResponse(tag.getId());
    }

    private ViewerTagEntity requireActiveTag(long id) {
        return tagRepository
                .findByIdAndDeletedTimeIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("标签不存在"));
    }

    private ViewerTagGroupEntity requireGroup(long groupId) {
        return groupRepository
                .findByIdAndDeletedTimeIsNull(groupId)
                .orElseThrow(() -> new IllegalArgumentException("所属分组不存在"));
    }

    private ViewerTagGroupEntity resolveGroup(Long groupId) {
        if (groupId == null) {
            return groupRepository
                    .findFirstByIsDefaultAndDeletedTimeIsNull(1)
                    .orElseThrow(() -> new IllegalArgumentException("默认分组不存在"));
        }
        return requireGroup(groupId);
    }

    private static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("标签名称不能为空");
        }
        String name = raw.trim();
        if (name.length() > 20) {
            throw new IllegalArgumentException("标签名称最多20字");
        }
        return name;
    }

    private static String requireGroupName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        String name = raw.trim();
        if (name.length() > 10) {
            throw new IllegalArgumentException("分组名称最多10字");
        }
        return name;
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
    }

    private Map<Long, String> loadGroupNames(List<ViewerTagEntity> tags) {
        Set<Long> ids = tags.stream().map(ViewerTagEntity::getGroupId).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (ViewerTagGroupEntity g : groupRepository.findAllById(ids)) {
            map.put(g.getId(), g.getName());
        }
        return map;
    }

    private Map<Long, Long> loadUserCounts(List<ViewerTagEntity> tags) {
        List<Long> ids = tags.stream().map(ViewerTagEntity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (ViewerTagMemberRepository.TagMemberCount row : memberRepository.countGroupedByTagId(ids)) {
            map.put(row.getTagId(), row.getCnt());
        }
        return map;
    }

    private Map<Long, Long> loadTagCounts(List<ViewerTagGroupEntity> groups) {
        List<Long> ids = groups.stream().map(ViewerTagGroupEntity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (ViewerTagRepository.GroupTagCount row : tagRepository.countActiveGroupedByGroupId(ids)) {
            map.put(row.getGroupId(), row.getCnt());
        }
        return map;
    }

    private static ViewerTagGroupPageItem toGroupPageItem(ViewerTagGroupEntity group, long tagCount) {
        return new ViewerTagGroupPageItem(group.getId(), group.getName(), group.getIsDefault() == 1, tagCount);
    }

    private static ViewerTagItem toItem(ViewerTagEntity tag, String groupName, long userCount) {
        ViewerTagItem item = new ViewerTagItem();
        item.setId(tag.getId());
        item.setName(tag.getName());
        item.setType(tag.getType());
        if (tag.getType() == ViewerTagType.AUTO
                && tag.getDefinitionSummary() != null
                && !tag.getDefinitionSummary().isBlank()) {
            item.setDefinitionSummary(tag.getDefinitionSummary());
        }
        item.setUserCount(userCount);
        item.setGroupId(tag.getGroupId());
        item.setGroupName(groupName == null ? "" : groupName);
        if (tag.getCreatedTime() != null) {
            item.setCreatedTime(tag.getCreatedTime().format(TIME_FMT));
        }
        return item;
    }
}
