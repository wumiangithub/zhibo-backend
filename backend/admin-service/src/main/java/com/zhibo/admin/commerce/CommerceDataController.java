package com.zhibo.admin.commerce;

import com.zhibo.admin.commerce.dto.ActivityCommerceDataResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class CommerceDataController {

    private final CommerceDataService commerceDataService;

    @GetMapping("/commerce-data/{id}")
    public ApiResponse<ActivityCommerceDataResponse> commerceData(@PathVariable String id) {
        return ApiResponse.ok(commerceDataService.commerceData(id));
    }
}
