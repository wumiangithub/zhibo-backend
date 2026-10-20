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

/** 进出房辅通道。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/watch/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/enter/{id}")
    public ApiResponse<EnterAttendanceResponse> enter(
            @PathVariable String id,
            @Valid @RequestBody EnterAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.enter(id, request));
    }

    @PostMapping("/leave/{id}")
    public ApiResponse<LeaveAttendanceResponse> leave(
            @PathVariable String id,
            @Valid @RequestBody LeaveAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.leave(id, request));
    }
}
