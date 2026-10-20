package com.zhibo.live.watch;

import com.zhibo.attendance.AttendeePage;
import com.zhibo.attendance.AttendeeRoster;
import com.zhibo.live.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 中控台名单。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/watch")
@RequiredArgsConstructor
public class WatchAttendeeController {

    private final AttendeeRoster roster;

    @GetMapping("/attendees/{id}")
    public ApiResponse<AttendeePage> list(
            @PathVariable String id,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(roster.list(id, tab, page, pageSize, keyword));
    }
}
