package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.EnterAttendanceRequest;
import com.zhibo.live.watch.dto.EnterAttendanceResponse;
import com.zhibo.live.watch.dto.LeaveAttendanceRequest;
import com.zhibo.live.watch.dto.LeaveAttendanceResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watch/{id}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/enter")
    public ApiResponse<EnterAttendanceResponse> enter(
            @PathVariable String id,
            @Valid @RequestBody EnterAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.enter(id, request));
    }

    @PostMapping("/leave")
    public ApiResponse<LeaveAttendanceResponse> leave(
            @PathVariable String id,
            @Valid @RequestBody LeaveAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.leave(id, request));
    }
}
