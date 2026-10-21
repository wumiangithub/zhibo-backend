package com.zhibo.admin.livereport;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/live-reports")
@RequiredArgsConstructor
public class LiveReportController {
    private final LiveReportService service;

    @PostMapping("/create")
    public ApiResponse<LiveReportDtos.Result> create(
            @Valid @RequestBody LiveReportDtos.SaveRequest body, HttpServletRequest r) {
        return ApiResponse.ok(service.create(body, userId(r)));
    }

    @GetMapping("/list")
    public ApiResponse<LiveReportDtos.ListResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String hostName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(service.list(page, pageSize, title, hostName, status, startFrom, startTo));
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<LiveReportDtos.Detail> detail(@PathVariable String id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PutMapping("/update/{id}")
    public ApiResponse<LiveReportDtos.Result> update(
            @PathVariable String id,
            @RequestBody LiveReportDtos.UpdateRequest body,
            HttpServletRequest r) {
        return ApiResponse.ok(service.update(id, body, userId(r)));
    }

    @PostMapping("/audit/{id}")
    public ApiResponse<LiveReportDtos.Result> audit(
            @PathVariable String id,
            @Valid @RequestBody LiveReportDtos.AuditRequest body,
            HttpServletRequest r) {
        return ApiResponse.ok(service.audit(id, body, userId(r)));
    }

    @GetMapping("/audits/{id}")
    public ApiResponse<LiveReportDtos.AuditList> audits(@PathVariable String id) {
        return ApiResponse.ok(service.audits(id));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<LiveReportDtos.DeleteResult> softDelete(
            @PathVariable String id, HttpServletRequest r) {
        return ApiResponse.ok(service.softDelete(id, userId(r)));
    }

    private static long userId(HttpServletRequest r) {
        Object v = r.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (v instanceof Number n) return n.longValue();
        throw new AuthException(401, "未登录");
    }
}
