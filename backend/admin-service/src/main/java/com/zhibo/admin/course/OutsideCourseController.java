package com.zhibo.admin.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.course.dto.OutsideCourseListRequest;
import com.zhibo.admin.course.dto.OutsideCourseListResponse;
import com.zhibo.admin.course.dto.OutsideOrderReceiveItem;
import com.zhibo.admin.web.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 第三方课程列表与订单回调。当前不鉴权，管理端登录拦截器已放行 {@code /api/v1/outside/**}。 */
@RestController
@RequestMapping("/api/v1/outside/courses")
@RequiredArgsConstructor
public class OutsideCourseController {

    private final CourseService courseService;
    private final OutsideOrderService outsideOrderService;
    private final ObjectMapper objectMapper;

    @PostMapping("/list")
    public ApiResponse<OutsideCourseListResponse> list(
            @RequestBody(required = false) OutsideCourseListRequest request) {
        OutsideCourseListRequest body = request == null ? new OutsideCourseListRequest() : request;
        return ApiResponse.ok(courseService.outsideList(body));
    }

    @PostMapping("/orders/receive")
    public ApiResponse<Void> receive(@RequestBody(required = false) JsonNode body) {
        if (body == null || body.isNull() || !body.isArray() || body.isEmpty()) {
            throw new IllegalArgumentException("请求体必须是订单数组");
        }
        List<OutsideOrderReceiveItem> orders;
        try {
            orders = objectMapper.convertValue(body, new TypeReference<List<OutsideOrderReceiveItem>>() {});
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("订单参数格式错误");
        }
        outsideOrderService.receive(orders);
        return ApiResponse.ok();
    }
}
