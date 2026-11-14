package com.zhibo.admin.quicksend;

import com.zhibo.admin.material.dto.DeleteIdResponse;
import com.zhibo.admin.quicksend.dto.BatchDeleteQuickSendMessagesRequest;
import com.zhibo.admin.quicksend.dto.BatchDeleteQuickSendMessagesResponse;
import com.zhibo.admin.quicksend.dto.CreateQuickSendGroupRequest;
import com.zhibo.admin.quicksend.dto.CreateQuickSendMessageRequest;
import com.zhibo.admin.quicksend.dto.QuickSendGroupResponse;
import com.zhibo.admin.quicksend.dto.QuickSendMessageListResponse;
import com.zhibo.admin.quicksend.dto.QuickSendMessageResponse;
import com.zhibo.admin.quicksend.dto.RenameQuickSendGroupRequest;
import com.zhibo.admin.quicksend.dto.ReorderQuickSendMessagesRequest;
import com.zhibo.admin.quicksend.dto.UpdateQuickSendMessageRequest;
import com.zhibo.common.UuidIds;
import com.zhibo.quicksend.QuickSendGroupEntity;
import com.zhibo.quicksend.QuickSendGroupRepository;
import com.zhibo.quicksend.QuickSendMessageEntity;
import com.zhibo.quicksend.QuickSendMessageRepository;
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
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuickSendService {

    private static final int CONTENT_MAX = 100;
    private static final int GROUP_NAME_MAX = 64;
    private static final int MAX_BATCH_DELETE = 100;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QuickSendGroupRepository groupRepository;
    private final QuickSendMessageRepository messageRepository;

    public List<QuickSendGroupResponse> listGroups() {
        return groupRepository.findAllByOrderByDefaultGroupDescSortOrderAscIdAsc().stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Transactional
    public QuickSendGroupResponse createGroup(CreateQuickSendGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = requireGroupName(request.getName());
        if (groupRepository.existsByName(name)) {
            throw new IllegalArgumentException("分组名称已存在");
        }
        QuickSendGroupEntity entity = new QuickSendGroupEntity();
        entity.setName(name);
        entity.setDefaultGroup(false);
        entity.setSortOrder(nextGroupSortOrder());
        groupRepository.save(entity);
        return toGroupResponse(entity);
    }

    @Transactional
    public QuickSendGroupResponse renameGroup(String id, RenameQuickSendGroupRequest request) {
        QuickSendGroupEntity entity = requireGroup(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = requireGroupName(request.getName());
        if (!name.equals(entity.getName()) && groupRepository.existsByName(name)) {
            throw new IllegalArgumentException("分组名称已存在");
        }
        entity.setName(name);
        groupRepository.save(entity);
        return toGroupResponse(entity);
    }

    @Transactional
    public DeleteIdResponse deleteGroup(String id) {
        QuickSendGroupEntity entity = requireGroup(id);
        if (entity.isDefaultGroup()) {
            throw new IllegalArgumentException("默认分组不可删除");
        }
        long count = messageRepository.countByGroupIdAndDeletedTimeIsNull(entity.getId());
        if (count > 0) {
            throw new IllegalArgumentException("分组下仍有消息");
        }
        groupRepository.delete(entity);
        return new DeleteIdResponse(entity.getId());
    }

    public QuickSendMessageListResponse listMessages(String groupId, int page, int pageSize) {
        String gid = UuidIds.require(groupId, "groupId 不能为空", "groupId 无效");
        requireGroup(gid);
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        Page<QuickSendMessageEntity> result =
                messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(
                        gid, PageRequest.of(page - 1, pageSize));
        QuickSendMessageListResponse response = new QuickSendMessageListResponse();
        response.setTotal(result.getTotalElements());
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setList(result.getContent().stream().map(this::toMessageResponse).toList());
        return response;
    }

    @Transactional
    public QuickSendMessageResponse createMessage(CreateQuickSendMessageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String gid = UuidIds.require(request.getGroupId(), "groupId 不能为空", "groupId 无效");
        requireGroup(gid);
        String content = requireContent(request.getContent());
        String anchorId = blankToNull(request.getAnchorId());
        String place = blankToNull(request.getPlace());

        List<QuickSendMessageEntity> ordered =
                new ArrayList<>(
                        messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(
                                gid));
        int insertAt = ordered.size();
        if (anchorId != null) {
            String aid = UuidIds.require(anchorId, "anchorId 不能为空", "anchorId 无效");
            if (place == null) {
                throw new IllegalArgumentException("有锚点时 place 必填（above 或 below）");
            }
            String p = place.toLowerCase(Locale.ROOT);
            if (!"above".equals(p) && !"below".equals(p)) {
                throw new IllegalArgumentException("place 须为 above 或 below");
            }
            int idx = indexOfMessage(ordered, aid);
            if (idx < 0) {
                throw new IllegalArgumentException("锚点消息不存在或不属于该分组");
            }
            insertAt = "above".equals(p) ? idx : idx + 1;
        } else if (place != null) {
            throw new IllegalArgumentException("无锚点时不要传 place");
        }

        QuickSendMessageEntity entity = new QuickSendMessageEntity();
        entity.setGroupId(gid);
        entity.setContent(content);
        ordered.add(insertAt, entity);
        renumberAndSave(ordered);
        return toMessageResponse(entity);
    }

    @Transactional
    public QuickSendMessageResponse updateMessage(String id, UpdateQuickSendMessageRequest request) {
        QuickSendMessageEntity entity = requireMessage(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        entity.setContent(requireContent(request.getContent()));
        messageRepository.save(entity);
        return toMessageResponse(entity);
    }

    @Transactional
    public QuickSendMessageResponse copyMessage(String id) {
        QuickSendMessageEntity source = requireMessage(id);
        CreateQuickSendMessageRequest req = new CreateQuickSendMessageRequest();
        req.setGroupId(source.getGroupId());
        req.setContent(source.getContent());
        req.setAnchorId(source.getId());
        req.setPlace("below");
        return createMessage(req);
    }

    @Transactional
    public void reorderMessages(ReorderQuickSendMessagesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String gid = UuidIds.require(request.getGroupId(), "groupId 不能为空", "groupId 无效");
        requireGroup(gid);
        List<String> ids = request.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        List<QuickSendMessageEntity> current =
                messageRepository.findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(gid);
        if (ids.size() != current.size()) {
            throw new IllegalArgumentException("ids 须覆盖该分组全部未软删消息");
        }
        Set<String> expected = new HashSet<>();
        for (QuickSendMessageEntity m : current) {
            expected.add(m.getId());
        }
        Set<String> seen = new HashSet<>();
        List<QuickSendMessageEntity> byIdOrder = new ArrayList<>(ids.size());
        for (String raw : ids) {
            String mid = UuidIds.require(raw, "ids 含空 id", "ids 含无效 id");
            if (!seen.add(mid)) {
                throw new IllegalArgumentException("ids 不可重复");
            }
            if (!expected.contains(mid)) {
                throw new IllegalArgumentException("ids 须与该分组未软删消息集合一致");
            }
            QuickSendMessageEntity found =
                    current.stream().filter(m -> m.getId().equals(mid)).findFirst().orElseThrow();
            byIdOrder.add(found);
        }
        renumberAndSave(byIdOrder);
    }

    @Transactional
    public DeleteIdResponse deleteMessage(String id) {
        QuickSendMessageEntity entity = requireMessage(id);
        entity.setDeletedTime(LocalDateTime.now());
        messageRepository.save(entity);
        return new DeleteIdResponse(entity.getId());
    }

    @Transactional
    public BatchDeleteQuickSendMessagesResponse batchDelete(BatchDeleteQuickSendMessagesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        List<String> ids = request.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        if (ids.size() > MAX_BATCH_DELETE) {
            throw new IllegalArgumentException("ids 最多 100 条");
        }
        Set<String> normalized = new HashSet<>();
        for (String raw : ids) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            normalized.add(UuidIds.require(raw.trim(), "ids 含空 id", "ids 含无效 id"));
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("没有可删除的消息");
        }
        List<QuickSendMessageEntity> found =
                messageRepository.findByIdInAndDeletedTimeIsNull(normalized);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("没有可删除的消息");
        }
        LocalDateTime now = LocalDateTime.now();
        for (QuickSendMessageEntity m : found) {
            m.setDeletedTime(now);
        }
        messageRepository.saveAll(found);
        return new BatchDeleteQuickSendMessagesResponse(found.size());
    }

    private void renumberAndSave(List<QuickSendMessageEntity> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setSortOrder(i + 1);
        }
        messageRepository.saveAll(ordered);
    }

    private static int indexOfMessage(List<QuickSendMessageEntity> ordered, String id) {
        for (int i = 0; i < ordered.size(); i++) {
            if (Objects.equals(ordered.get(i).getId(), id)) {
                return i;
            }
        }
        return -1;
    }

    private QuickSendGroupEntity requireGroup(String id) {
        String gid = UuidIds.require(id);
        return groupRepository
                .findById(gid)
                .orElseThrow(() -> new IllegalArgumentException("分组不存在"));
    }

    private QuickSendMessageEntity requireMessage(String id) {
        String mid = UuidIds.require(id);
        return messageRepository
                .findByIdAndDeletedTimeIsNull(mid)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
    }

    private int nextGroupSortOrder() {
        return groupRepository.findAllByOrderByDefaultGroupDescSortOrderAscIdAsc().stream()
                .mapToInt(QuickSendGroupEntity::getSortOrder)
                .max()
                .orElse(0)
                + 1;
    }

    private static String requireGroupName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("分组名称不能为空");
        }
        if (name.length() > GROUP_NAME_MAX) {
            throw new IllegalArgumentException("分组名称不能超过 64 字");
        }
        return name;
    }

    private static String requireContent(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        String content = raw.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new IllegalArgumentException("消息内容不能超过 100 字");
        }
        return content;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private QuickSendGroupResponse toGroupResponse(QuickSendGroupEntity entity) {
        long count = messageRepository.countByGroupIdAndDeletedTimeIsNull(entity.getId());
        return new QuickSendGroupResponse(
                entity.getId(), entity.getName(), count, entity.isDefaultGroup());
    }

    private QuickSendMessageResponse toMessageResponse(QuickSendMessageEntity entity) {
        QuickSendMessageResponse item = new QuickSendMessageResponse();
        item.setId(entity.getId());
        item.setGroupId(entity.getGroupId());
        item.setContent(entity.getContent());
        item.setSortOrder(entity.getSortOrder());
        if (entity.getCreatedTime() != null) {
            item.setCreatedTime(entity.getCreatedTime().format(TIME_FMT));
        }
        if (entity.getUpdatedTime() != null) {
            item.setUpdatedTime(entity.getUpdatedTime().format(TIME_FMT));
        }
        return item;
    }
}
