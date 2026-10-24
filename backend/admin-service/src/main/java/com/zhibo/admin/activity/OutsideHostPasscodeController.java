package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.PasscodeEnterRequest;
import com.zhibo.admin.activity.dto.PasscodeEnterResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 口令开播公开入口。当前不鉴权（{@code /api/v1/outside/**}），H5 与以后 App 共用。
 */
@RestController
@RequestMapping("/api/v1/outside/host/passcode")
@RequiredArgsConstructor
public class OutsideHostPasscodeController {

    private final HostPasscodeService hostPasscodeService;

    @PostMapping("/enter")
    public ApiResponse<PasscodeEnterResponse> enter(@Valid @RequestBody PasscodeEnterRequest request) {
        return ApiResponse.ok(hostPasscodeService.enter(request));
    }
}
