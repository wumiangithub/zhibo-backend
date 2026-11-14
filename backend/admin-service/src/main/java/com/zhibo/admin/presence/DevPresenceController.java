package com.zhibo.admin.presence;

import com.zhibo.admin.presence.dto.DevPresenceRequest;
import com.zhibo.admin.presence.dto.DevPresenceResponse;
import com.zhibo.admin.web.ApiResponse;
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
 * 管理端本地模拟观众上下线。非 local profile 业务 404。
 */
@RestController
@RequestMapping("/api/v1/activities/dev")
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
