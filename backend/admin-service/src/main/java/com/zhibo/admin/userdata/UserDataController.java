package com.zhibo.admin.userdata;

import com.zhibo.admin.userdata.dto.ActivityUserDataResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class UserDataController {

    private final UserDataService userDataService;

    @GetMapping("/user-data/{id}")
    public ApiResponse<ActivityUserDataResponse> userData(@PathVariable String id) {
        return ApiResponse.ok(userDataService.userData(id));
    }
}
