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

@RestController
@RequestMapping("/api/watch/{id}/attendees")
@RequiredArgsConstructor
public class WatchAttendeeController {

    private final AttendeeRoster roster;

    @GetMapping
    public ApiResponse<AttendeePage> list(
            @PathVariable String id,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(roster.list(id, tab, page, pageSize, keyword));
    }
}
