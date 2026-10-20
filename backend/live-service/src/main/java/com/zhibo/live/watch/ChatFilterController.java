package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.ChatFilterResponse;
import com.zhibo.live.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 聊天审核页 URL。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/watch")
@RequiredArgsConstructor
public class ChatFilterController {

    private final ChatFilterService chatFilterService;

    @GetMapping("/chat-filter/{id}")
    public ApiResponse<ChatFilterResponse> chatFilter(
            @PathVariable String id,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name) {
        return ApiResponse.ok(chatFilterService.chatFilter(id, email, name));
    }
}
