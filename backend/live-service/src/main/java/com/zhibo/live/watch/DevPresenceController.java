package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.DevPresenceRequest;
import com.zhibo.live.watch.dto.DevPresenceResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地开发模拟微吼上下线回调。非 local profile 业务 404。
 */
@RestController
@RequestMapping("/api/v1/watch/dev")
@RequiredArgsConstructor
public class DevPresenceController {

    private final DevPresenceService devPresenceService;
    private final Environment environment;

    @PostMapping("/presence/{id}")
    public ApiResponse<DevPresenceResponse> presence(
            @PathVariable String id,
            @Valid @RequestBody DevPresenceRequest request) {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            return ApiResponse.fail(404, "接口不存在");
        }
        return ApiResponse.ok(devPresenceService.simulate(id, request));
    }
}
