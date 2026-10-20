package com.zhibo.admin.employee;

import com.zhibo.admin.employee.dto.EmployeeListResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ApiResponse<EmployeeListResponse> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(employeeService.list(keyword));
    }
}
