package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerJwtTokenService;
import com.zhibo.live.watch.dto.VerifyPasswordRequest;
import com.zhibo.live.watch.dto.VerifyPasswordResponse;
import com.zhibo.live.watch.dto.WatchAccessResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观看售卖门禁（D90）。 */
@RestController
@RequestMapping("/api/v1/watch/access")
@RequiredArgsConstructor
public class WatchAccessController {

    private final WatchAccessService watchAccessService;
    private final ViewerJwtTokenService viewerJwtTokenService;

    @GetMapping("/{id}")
    public ApiResponse<WatchAccessResponse> access(
            @PathVariable String id,
            @RequestHeader(value = WatchAccessService.HEADER_ACTIVITY_ACCESS, required = false)
                    String activityAccess,
            HttpServletRequest request) {
        Long viewerId = tryParseViewerId(request);
        return ApiResponse.ok(watchAccessService.access(id, viewerId, activityAccess));
    }

    @PostMapping("/verify-password/{id}")
    public ApiResponse<VerifyPasswordResponse> verifyPassword(
            @PathVariable String id,
            @Valid @RequestBody VerifyPasswordRequest body) {
        return ApiResponse.ok(watchAccessService.verifyPassword(id, body.getPassword()));
    }

    private Long tryParseViewerId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            return viewerJwtTokenService.parseUserId(token);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
