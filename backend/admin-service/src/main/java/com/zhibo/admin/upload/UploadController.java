package com.zhibo.admin.upload;

import com.zhibo.admin.upload.dto.TosStsResponse;
import com.zhibo.admin.upload.dto.UploadImageResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传相关：微吼图片代理 + 火山 TOS STS 临时凭证。
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final TosStsService tosStsService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadImageResponse> uploadImage(
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.uploadImage(file));
    }

    /** 浏览器直传 TOS 用的短期凭证；不收文件。 */
    @GetMapping("/tos-sts")
    public ApiResponse<TosStsResponse> tosSts() {
        return ApiResponse.ok(tosStsService.issueCredentials());
    }
}
