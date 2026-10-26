package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerTosStsResponse;
import com.zhibo.live.viewer.dto.ViewerUploadResult;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 观众上传：直传凭证，以及与直传结果同形的文件中转。不写业务表。 */
@RestController
@RequestMapping("/api/v1/viewer/uploads")
public class ViewerUploadController {

    private final ViewerTosStsService viewerTosStsService;
    private final ViewerFileUploadService viewerFileUploadService;

    public ViewerUploadController(
            ViewerTosStsService viewerTosStsService, ViewerFileUploadService viewerFileUploadService) {
        this.viewerTosStsService = viewerTosStsService;
        this.viewerFileUploadService = viewerFileUploadService;
    }

    /** 直传临时凭证，对齐管理端：无 Query biz；前缀由前端拼 objectKey。 */
    @GetMapping("/tos-sts")
    public ApiResponse<ViewerTosStsResponse> tosSts(HttpServletRequest request) {
        requireViewerId(request);
        return ApiResponse.ok(viewerTosStsService.issueCredentials());
    }

    /**
     * 中转上传。表单字段与直传 PutObject 对齐：objectKey + file（可选 contentType）。
     * 返回 objectKey、url、location。
     */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ViewerUploadResult> file(
            @RequestParam(value = "biz", required = false) Integer biz,
            @RequestParam(value = "objectKey", required = false) String objectKey,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) {
        requireViewerId(request);
        if (biz == null) {
            throw new ViewerAuthException(400, "请指定上传用途");
        }
        return ApiResponse.ok(viewerFileUploadService.upload(biz, objectKey, file, contentType));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
