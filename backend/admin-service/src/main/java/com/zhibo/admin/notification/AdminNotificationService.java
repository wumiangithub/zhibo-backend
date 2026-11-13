package com.zhibo.admin.notification;

import com.zhibo.admin.notification.dto.NotificationIdResponse;
import com.zhibo.admin.notification.dto.NotificationItemResponse;
import com.zhibo.admin.notification.dto.NotificationListResponse;
import com.zhibo.admin.notification.dto.NotificationReadAllResponse;
import com.zhibo.admin.notification.dto.NotificationUnreadCountResponse;
import com.zhibo.notification.AdminNotificationAuditResult;
import com.zhibo.notification.AdminNotificationEntity;
import com.zhibo.notification.AdminNotificationInboxEntity;
import com.zhibo.notification.AdminNotificationInboxId;
import com.zhibo.notification.AdminNotificationInboxRepository;
import com.zhibo.notification.AdminNotificationRefType;
import com.zhibo.notification.AdminNotificationRepository;
import com.zhibo.notification.AdminNotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 管理端通知中心：审核事件写入收件箱；列表 / 未读 / 已读只看当前员工。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int BODY_MAX = 512;

    private final AdminNotificationRepository notificationRepository;
    private final AdminNotificationInboxRepository inboxRepository;
    private final Clock clock;

    /**
     * 写入失败只打日志，不抛，避免拖垮审核主流程。
     */
    public void notifyQuietly(
            int type,
            int auditResult,
            String category,
            String title,
            String body,
            int refType,
            String refId,
            Collection<Long> recipientStaffIds) {
        try {
            create(type, auditResult, category, title, body, refType, refId, recipientStaffIds);
        } catch (Exception ex) {
            log.warn(
                    "admin notification write failed type={} refType={} refId={}: {}",
                    type,
                    refType,
                    refId,
                    ex.toString());
        }
    }

    @Transactional
    public void create(
            int type,
            int auditResult,
            String category,
            String title,
            String body,
            int refType,
            String refId,
            Collection<Long> recipientStaffIds) {
        if (!AdminNotificationType.isValid(type)) {
            throw new IllegalArgumentException("通知类型无效");
        }
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("关联业务 id 不能为空");
        }
        Set<Long> recipients = distinctPositive(recipientStaffIds);
        if (recipients.isEmpty()) {
            return;
        }

        AdminNotificationEntity row = new AdminNotificationEntity();
        row.setType(type);
        row.setAuditResult(auditResult);
        row.setCategory(trimTo(category, 32));
        row.setTitle(trimTo(title, 64));
        row.setBody(trimTo(body == null ? "" : body, BODY_MAX));
        row.setRefType(refType);
        row.setRefId(refId.trim());
        notificationRepository.save(row);

        List<AdminNotificationInboxEntity> inboxRows = new ArrayList<>(recipients.size());
        for (Long staffId : recipients) {
            AdminNotificationInboxEntity inbox = new AdminNotificationInboxEntity();
            inbox.setId(new AdminNotificationInboxId(row.getId(), staffId));
            inbox.setReadTime(null);
            inboxRows.add(inbox);
        }
        inboxRepository.saveAll(inboxRows);
    }

    /** 素材 AI / 风控审核结果 → type=4，收件人=上传者。 */
    public void notifyMaterialAuditQuietly(
            String materialId, String materialName, String action, int status, Long uploaderId) {
        if (uploaderId == null || uploaderId <= 0) {
            return;
        }
        int auditResult =
                status == 1
                        ? AdminNotificationAuditResult.PASSED
                        : AdminNotificationAuditResult.REJECTED;
        String prefix = "ai".equals(action) ? "AI审核" : "风控合规";
        String resultLabel = auditResult == AdminNotificationAuditResult.PASSED ? "已通过" : "已驳回";
        String name = materialName == null || materialName.isBlank() ? materialId : materialName.trim();
        String body = prefix + "-" + resultLabel + "：素材「" + name + "」";
        notifyQuietly(
                AdminNotificationType.MATERIAL_AUDIT,
                auditResult,
                "素材库",
                "合规风控审核结果通知",
                body,
                AdminNotificationRefType.MATERIAL,
                materialId,
                List.of(uploaderId));
    }

    /** 报备审核 → type=2，收件人=提交人。 */
    public void notifyLiveReportAuditQuietly(
            String reportId, String reportTitle, String result, Long submitterId) {
        if (submitterId == null || submitterId <= 0) {
            return;
        }
        int auditResult = toAuditResult(result);
        if (auditResult == AdminNotificationAuditResult.NONE) {
            return;
        }
        String resultLabel =
                auditResult == AdminNotificationAuditResult.PASSED ? "已通过" : "已驳回";
        String titleHint =
                reportTitle == null || reportTitle.isBlank() ? reportId : reportTitle.trim();
        String body = "审核-" + resultLabel + "：报备「" + titleHint + "」";
        notifyQuietly(
                AdminNotificationType.LIVE_REPORT_AUDIT,
                auditResult,
                "直播报备",
                "合规风控审核结果通知",
                body,
                AdminNotificationRefType.LIVE_REPORT,
                reportId,
                List.of(submitterId));
    }

    /** 直播审核 → type=3，收件人=全部归属人。 */
    public void notifyLiveAuditQuietly(
            String activityId, String activityTitle, String result, Collection<Long> ownerIds) {
        int auditResult = toAuditResult(result);
        if (auditResult == AdminNotificationAuditResult.NONE) {
            return;
        }
        String name =
                activityTitle == null || activityTitle.isBlank() ? activityId : activityTitle.trim();
        String body =
                auditResult == AdminNotificationAuditResult.PASSED
                        ? "直播「" + name + "」审核通过，可正常开播。"
                        : "直播「" + name + "」审核未通过。";
        notifyQuietly(
                AdminNotificationType.LIVE_AUDIT,
                auditResult,
                "直播",
                "合规风控审核结果通知",
                body,
                AdminNotificationRefType.ACTIVITY,
                activityId,
                ownerIds);
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(
            long staffId, Integer page, Integer pageSize, String keyword, Integer type, Integer auditResult) {
        int p = page == null ? 1 : page;
        if (p < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }

        Integer typeFilter = null;
        if (type != null && type != 0) {
            if (!AdminNotificationType.isValid(type)) {
                throw new IllegalArgumentException("type 无效");
            }
            typeFilter = type;
        }

        Integer auditFilter = null;
        if (auditResult != null && auditResult != 0) {
            if (auditResult != AdminNotificationAuditResult.PASSED
                    && auditResult != AdminNotificationAuditResult.REJECTED) {
                throw new IllegalArgumentException("auditResult 无效");
            }
            auditFilter = auditResult;
        }

        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }

        Page<AdminNotificationEntity> result =
                notificationRepository.searchForStaff(
                        staffId, typeFilter, auditFilter, kw, PageRequest.of(p - 1, size));

        Map<String, LocalDateTime> readById = new HashMap<>();
        List<String> ids =
                result.getContent().stream().map(AdminNotificationEntity::getId).toList();
        if (!ids.isEmpty()) {
            for (AdminNotificationInboxEntity inbox :
                    inboxRepository.findByIdStaffIdAndIdNotificationIdIn(staffId, ids)) {
                readById.put(inbox.getId().getNotificationId(), inbox.getReadTime());
            }
        }

        NotificationListResponse response = new NotificationListResponse();
        response.setPage(p);
        response.setPageSize(size);
        response.setTotal(result.getTotalElements());
        List<NotificationItemResponse> list = new ArrayList<>(result.getContent().size());
        for (AdminNotificationEntity row : result.getContent()) {
            list.add(toItem(row, readById.get(row.getId()) == null));
        }
        response.setList(list);
        return response;
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse unreadCount(long staffId) {
        return new NotificationUnreadCountResponse(
                inboxRepository.countByIdStaffIdAndReadTimeIsNull(staffId));
    }

    @Transactional
    public NotificationIdResponse readOne(long staffId, String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("通知不存在");
        }
        String id = notificationId.trim().replace("-", "").toLowerCase(Locale.ROOT);
        AdminNotificationInboxEntity inbox =
                inboxRepository
                        .findByIdNotificationIdAndIdStaffId(id, staffId)
                        .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (inbox.getReadTime() == null) {
            inbox.setReadTime(LocalDateTime.now(clock));
            inboxRepository.save(inbox);
        }
        return new NotificationIdResponse(id);
    }

    @Transactional
    public NotificationReadAllResponse readAll(long staffId) {
        int marked = inboxRepository.markAllRead(staffId, LocalDateTime.now(clock));
        return new NotificationReadAllResponse(marked);
    }

    private NotificationItemResponse toItem(AdminNotificationEntity row, boolean unread) {
        NotificationItemResponse item = new NotificationItemResponse();
        item.setId(row.getId());
        item.setType(row.getType());
        item.setCategory(row.getCategory());
        item.setTitle(row.getTitle());
        item.setBody(row.getBody());
        item.setAuditResult(row.getAuditResult());
        item.setRefType(row.getRefType());
        item.setRefId(row.getRefId());
        item.setUnread(unread);
        item.setCreatedTime(
                row.getCreatedTime() == null ? "" : TIME.format(row.getCreatedTime()));
        return item;
    }

    private static int toAuditResult(String result) {
        if ("passed".equals(result)) {
            return AdminNotificationAuditResult.PASSED;
        }
        if ("rejected".equals(result)) {
            return AdminNotificationAuditResult.REJECTED;
        }
        return AdminNotificationAuditResult.NONE;
    }

    private static Set<Long> distinctPositive(Collection<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        if (ids == null) {
            return out;
        }
        for (Long id : ids) {
            if (id != null && id > 0) {
                out.add(id);
            }
        }
        return out;
    }

    private static String trimTo(String raw, int max) {
        String value = Objects.requireNonNullElse(raw, "").trim();
        if (value.length() > max) {
            return value.substring(0, max);
        }
        return value;
    }
}
