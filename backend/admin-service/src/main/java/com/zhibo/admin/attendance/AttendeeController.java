package com.zhibo.admin.attendance;

import com.zhibo.admin.attendance.dto.AttendeeListResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class AttendeeController {

    private final AttendeeService attendeeService;

    /** 废弃：新代码用 live GET /api/v1/watch/attendees/{id}。 */
    @GetMapping("/attendees/{id}")
    public ApiResponse<AttendeeListResponse> list(
            @PathVariable String id,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(attendeeService.list(id, tab, page, pageSize, keyword));
    }
}
