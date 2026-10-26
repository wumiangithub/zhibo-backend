package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerTosStsResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观众端上传相关；直传 TOS 临时凭证。 */
@RestController
@RequestMapping("/api/v1/viewer/uploads")
public class ViewerUploadController {

    private final ViewerTosStsService viewerTosStsService;

    public ViewerUploadController(ViewerTosStsService viewerTosStsService) {
        this.viewerTosStsService = viewerTosStsService;
    }

    @GetMapping("/tos-sts")
    public ApiResponse<ViewerTosStsResponse> tosSts(HttpServletRequest request) {
        requireViewerId(request);
        return ApiResponse.ok(viewerTosStsService.issueCredentials());
    }

    private static void requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
    }
}
