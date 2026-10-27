package com.zhibo.live.callback;

import com.zhibo.live.callback.VhallLiveStateCallbackService.HandleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 微吼活动开始 / 结束回调入口。成功须返回纯文本 {@code success}。
 */
@RestController
@RequestMapping("/api/v1/callbacks/vhall")
@RequiredArgsConstructor
public class VhallLiveStateCallbackController {

    private final VhallLiveStateCallbackService callbackService;

    @PostMapping(value = "/live-state", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> liveState(@RequestBody Map<String, Object> body) {
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
