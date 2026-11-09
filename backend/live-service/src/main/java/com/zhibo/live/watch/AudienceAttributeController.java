package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.live.watch.dto.AttributeAudienceResponse;
import com.zhibo.live.watch.dto.AudienceSalesmanResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 邀请归因与本场业务员；观众 id 只从观众 JWT 取（D97）。 */
@RestController
@RequestMapping("/api/v1/watch/audience")
@RequiredArgsConstructor
public class AudienceAttributeController {

    private final AudienceAttributeService audienceAttributeService;

    /**
     * 本场业务员：读 {@code activity_audience}，优先员工邀请人，否则导入人。
     * 供观看页创建订单传订单中心 {@code salesmanId}。
     */
    @GetMapping("/salesman/{id}")
    public ApiResponse<AudienceSalesmanResponse> salesman(
            @PathVariable String id, HttpServletRequest httpRequest) {
        Long viewerId = (Long) httpRequest.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null || viewerId <= 0) {
            throw new ViewerAuthException(401, "未登录");
        }
        return ApiResponse.ok(audienceAttributeService.resolveSalesman(id, viewerId));
    }

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
