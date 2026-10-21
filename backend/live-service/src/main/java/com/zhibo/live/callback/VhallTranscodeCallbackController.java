package com.zhibo.live.callback;

import com.zhibo.live.callback.VhallTranscodeCallbackService.HandleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微吼转码进度 / 完成回调入口。成功须返回纯文本 {@code success}。
 */
@RestController
@RequestMapping("/api/v1/callbacks/vhall")
@RequiredArgsConstructor
public class VhallTranscodeCallbackController {

    private final VhallTranscodeCallbackService callbackService;

    @PostMapping(value = "/transcode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> transcode(@RequestBody Map<String, Object> body) {
        HandleResult result = callbackService.handle(body);
        if (result == HandleResult.OK || result == HandleResult.IGNORED) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("success");
        }
        if (result == HandleResult.BAD_SIGNATURE) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("bad signature");
        }
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body("bad request");
    }
}
