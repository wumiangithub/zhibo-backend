package com.zhibo.admin.attendance;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.admin.attendance.dto.AttendeeItem;
import com.zhibo.admin.attendance.dto.AttendeeListResponse;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.attendance.AttendeePage;
import com.zhibo.attendance.AttendeeRoster;
import com.zhibo.attendance.AttendeeRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 中控台名单（D110）。算法在 {@link AttendeeRoster}，这里转成管理端响应类型，
 * 并补学员侧跟进人姓名与发言数（左侧人员列表展示用）。
 */
@Service
@RequiredArgsConstructor
public class AttendeeService {

    private final AttendeeRoster roster;
    private final ActivityAudienceRepository audienceRepository;
    private final RoomCommentRepository commentRepository;
    private final SysUserAccountRepository userRepository;

    public AttendeeListResponse list(
            String activityId,
            String tab,
            int page,
            int pageSize,
            String keyword,
            String keywordType,
            String kind,
            String followerFilter,
            String behavior) {
        AttendeePage source = roster.list(
                activityId, tab, page, pageSize, keyword, keywordType, kind, followerFilter, behavior);
        AttendeeListResponse response = new AttendeeListResponse();
        response.setActivityState(source.activityState());
        response.setTotal(source.total());
        response.setStudentTotal(source.studentTotal());
        response.setAssistantTotal(source.assistantTotal());
        response.setAllTotal(source.allTotal());
        response.setOnlineTotal(source.onlineTotal());
        response.setOfflineTotal(source.offlineTotal());
        response.setPage(source.page());
        response.setPageSize(source.pageSize());

        List<AttendeeRow> rows = source.list();
        Set<String> studentUsernames = new HashSet<>();
        for (AttendeeRow row : rows) {
            if (row.username() != null && !isAssistantUsername(row.username())) {
                studentUsernames.add(row.username());
            }
        }
        Map<String, Long> inviterByUser = loadInviterByUsername(activityId, studentUsernames);
        Map<Long, String> employeeNames = loadEmployeeNames(inviterByUser.values());
        Map<String, Integer> commentByUser = loadCommentCounts(activityId, studentUsernames);

        List<AttendeeItem> list = new ArrayList<>();
        for (AttendeeRow row : rows) {
            AttendeeItem item = new AttendeeItem();
            item.setUsername(row.username());
            item.setNickname(row.nickname());
            item.setAvatarUrl(row.avatarUrl());
            item.setEnterTime(row.enterTime());
            item.setLeaveTime(row.leaveTime());
            item.setOnline(row.online());
            item.setRoleLabel(row.roleLabel());
            if (row.username() != null && !isAssistantUsername(row.username())) {
                Long inviterId = inviterByUser.get(row.username());
                if (inviterId != null && inviterId > 0) {
                    String name = employeeNames.get(inviterId);
                    item.setFollowerName(
                            name != null && !name.isBlank() ? name.trim() : "员工" + inviterId);
                }
                item.setCommentCount(commentByUser.getOrDefault(row.username(), 0));
            }
            list.add(item);
        }
        response.setList(list);
        return response;
    }

    private Map<String, Long> loadInviterByUsername(String activityId, Set<String> usernames) {
        Map<String, Long> map = new HashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        for (ActivityAudienceEntity audience :
                audienceRepository.findByActivityIdAndUsernameIn(activityId, usernames)) {
            if (audience.getUsername() == null) {
                continue;
            }
            Long inviter = audience.getEmployeeInviterId();
            if (inviter != null && inviter > 0) {
                map.put(audience.getUsername(), inviter);
            }
        }
        return map;
    }

    private Map<Long, String> loadEmployeeNames(Iterable<Long> ids) {
        Set<Long> unique = new HashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysUserAccountEntity user : userRepository.findAllById(unique)) {
            map.put(user.getId(), user.getUserName());
        }
        return map;
    }

    private Map<String, Integer> loadCommentCounts(String activityId, Set<String> usernames) {
        Map<String, Integer> map = new HashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        for (Object[] row : commentRepository.countActiveByUsernames(activityId, usernames)) {
            if (row[0] == null) {
                continue;
            }
            map.put((String) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private static boolean isAssistantUsername(String username) {
        return username != null && username.matches("^[ez]\\d+$");
    }
}
