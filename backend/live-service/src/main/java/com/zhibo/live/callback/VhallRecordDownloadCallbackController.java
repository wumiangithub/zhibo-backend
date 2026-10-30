package com.zhibo.live.callback;

import com.zhibo.live.callback.VhallRecordDownloadCallbackService.HandleResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微吼回放打包完成回调入口（事件 6 / {@code method_id=6}）。成功须返回纯文本 {@code success}（先回再异步）。
 */
@RestController
@RequestMapping("/api/v1/callbacks/vhall")
@RequiredArgsConstructor
public class VhallRecordDownloadCallbackController {

    private final VhallRecordDownloadCallbackService callbackService;

    @PostMapping(value = "/record-download", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordDownload(@RequestBody Map<String, Object> body) {
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
