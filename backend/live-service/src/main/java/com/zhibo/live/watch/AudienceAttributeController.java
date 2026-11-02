package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.live.watch.dto.AttributeAudienceResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 邀请归因；被邀请观众 id 只从观众 JWT 取（D97）。 */
@RestController
@RequestMapping("/api/v1/watch/audience")
@RequiredArgsConstructor
public class AudienceAttributeController {

    private final AudienceAttributeService audienceAttributeService;

    @PostMapping("/attribute/{id}")
    public ApiResponse<AttributeAudienceResponse> attribute(
            @PathVariable String id,
            @Valid @RequestBody AttributeAudienceRequest request,
            HttpServletRequest httpRequest) {
        Long viewerId = (Long) httpRequest.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null || viewerId <= 0) {
            throw new ViewerAuthException(401, "未登录");
        }
        return ApiResponse.ok(audienceAttributeService.attribute(id, viewerId, request));
    }
}
