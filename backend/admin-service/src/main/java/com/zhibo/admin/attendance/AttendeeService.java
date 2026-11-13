package com.zhibo.admin.attendance;

import com.zhibo.admin.attendance.dto.AttendeeItem;
import com.zhibo.admin.attendance.dto.AttendeeListResponse;
import com.zhibo.attendance.AttendeePage;
import com.zhibo.attendance.AttendeeRoster;
import com.zhibo.attendance.AttendeeRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 中控台名单（D110）。算法在 {@link AttendeeRoster}，这里转成管理端响应类型。
 */
@Service
@RequiredArgsConstructor
public class AttendeeService {

    private final AttendeeRoster roster;

    public AttendeeListResponse list(String activityId, String tab, int page, int pageSize, String keyword) {
        AttendeePage source = roster.list(activityId, tab, page, pageSize, keyword);
        AttendeeListResponse response = new AttendeeListResponse();
        response.setActivityState(source.activityState());
        response.setTotal(source.total());
        response.setPage(source.page());
        response.setPageSize(source.pageSize());
        List<AttendeeItem> list = new ArrayList<>();
        for (AttendeeRow row : source.list()) {
            AttendeeItem item = new AttendeeItem();
            item.setUsername(row.username());
            item.setNickname(row.nickname());
            item.setAvatarUrl(row.avatarUrl());
            item.setEnterTime(row.enterTime());
            item.setLeaveTime(row.leaveTime());
            item.setOnline(row.online());
            list.add(item);
        }
        response.setList(list);
        return response;
    }
}
