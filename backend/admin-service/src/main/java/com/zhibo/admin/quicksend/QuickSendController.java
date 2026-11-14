package com.zhibo.admin.quicksend;

import com.zhibo.admin.material.dto.DeleteIdResponse;
import com.zhibo.admin.quicksend.dto.BatchDeleteQuickSendMessagesRequest;
import com.zhibo.admin.quicksend.dto.BatchDeleteQuickSendMessagesResponse;
import com.zhibo.admin.quicksend.dto.CreateQuickSendGroupRequest;
import com.zhibo.admin.quicksend.dto.CreateQuickSendMessageRequest;
import com.zhibo.admin.quicksend.dto.QuickSendGroupResponse;
import com.zhibo.admin.quicksend.dto.QuickSendMessageListResponse;
import com.zhibo.admin.quicksend.dto.QuickSendMessageResponse;
import com.zhibo.admin.quicksend.dto.RenameQuickSendGroupRequest;
import com.zhibo.admin.quicksend.dto.ReorderQuickSendMessagesRequest;
import com.zhibo.admin.quicksend.dto.UpdateQuickSendMessageRequest;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 快捷发送话术库（D126）。路径命名 D62：动作在前、单 id 末尾。
 */
@RestController
@RequestMapping("/api/v1/quick-send")
@RequiredArgsConstructor
public class QuickSendController {

    private final QuickSendService quickSendService;

    @GetMapping("/groups/list")
    public ApiResponse<List<QuickSendGroupResponse>> listGroups() {
        return ApiResponse.ok(quickSendService.listGroups());
    }

    @PostMapping("/groups/create")
    public ApiResponse<QuickSendGroupResponse> createGroup(
            @RequestBody CreateQuickSendGroupRequest request) {
        return ApiResponse.ok(quickSendService.createGroup(request));
    }

    @PutMapping("/groups/rename/{id}")
    public ApiResponse<QuickSendGroupResponse> renameGroup(
            @PathVariable String id, @RequestBody RenameQuickSendGroupRequest request) {
        return ApiResponse.ok(quickSendService.renameGroup(id, request));
    }

    @DeleteMapping("/groups/delete/{id}")
    public ApiResponse<DeleteIdResponse> deleteGroup(@PathVariable String id) {
        return ApiResponse.ok(quickSendService.deleteGroup(id));
    }

    @GetMapping("/messages/list")
    public ApiResponse<QuickSendMessageListResponse> listMessages(
            @RequestParam String groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(quickSendService.listMessages(groupId, page, pageSize));
    }

    @PostMapping("/messages/create")
    public ApiResponse<QuickSendMessageResponse> createMessage(
            @RequestBody CreateQuickSendMessageRequest request) {
        return ApiResponse.ok(quickSendService.createMessage(request));
    }

    @PutMapping("/messages/update/{id}")
    public ApiResponse<QuickSendMessageResponse> updateMessage(
            @PathVariable String id, @RequestBody UpdateQuickSendMessageRequest request) {
        return ApiResponse.ok(quickSendService.updateMessage(id, request));
    }

    @PostMapping("/messages/copy/{id}")
    public ApiResponse<QuickSendMessageResponse> copyMessage(@PathVariable String id) {
        return ApiResponse.ok(quickSendService.copyMessage(id));
    }

    @PostMapping("/messages/reorder")
    public ApiResponse<Void> reorderMessages(@RequestBody ReorderQuickSendMessagesRequest request) {
        quickSendService.reorderMessages(request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/messages/delete/{id}")
    public ApiResponse<DeleteIdResponse> deleteMessage(@PathVariable String id) {
        return ApiResponse.ok(quickSendService.deleteMessage(id));
    }

    @PostMapping("/messages/batch-delete")
    public ApiResponse<BatchDeleteQuickSendMessagesResponse> batchDelete(
            @RequestBody BatchDeleteQuickSendMessagesRequest request) {
        return ApiResponse.ok(quickSendService.batchDelete(request));
    }
}
