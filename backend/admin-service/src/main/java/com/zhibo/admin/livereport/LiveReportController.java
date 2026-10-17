package com.zhibo.admin.livereport;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/live-reports") @RequiredArgsConstructor
public class LiveReportController {
    private final LiveReportService service;
    @PostMapping public ApiResponse<LiveReportDtos.Result> create(@Valid @RequestBody LiveReportDtos.SaveRequest body,HttpServletRequest r){return ApiResponse.ok(service.create(body,userId(r)));}
    @GetMapping public ApiResponse<LiveReportDtos.ListResult> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int pageSize,@RequestParam(required=false)String title,@RequestParam(required=false)String hostName,@RequestParam(required=false)String status,@RequestParam(required=false)String startFrom,@RequestParam(required=false)String startTo){return ApiResponse.ok(service.list(page,pageSize,title,hostName,status,startFrom,startTo));}
    @GetMapping("/{id}") public ApiResponse<LiveReportDtos.Detail> detail(@PathVariable String id){return ApiResponse.ok(service.detail(id));}
    @PutMapping("/{id}") public ApiResponse<LiveReportDtos.Result> update(@PathVariable String id,@RequestBody LiveReportDtos.UpdateRequest body,HttpServletRequest r){return ApiResponse.ok(service.update(id,body,userId(r)));}
    @PostMapping("/{id}/audit") public ApiResponse<LiveReportDtos.Result> audit(@PathVariable String id,@Valid @RequestBody LiveReportDtos.AuditRequest body,HttpServletRequest r){return ApiResponse.ok(service.audit(id,body,userId(r)));}
    @GetMapping("/{id}/audits") public ApiResponse<LiveReportDtos.AuditList> audits(@PathVariable String id){return ApiResponse.ok(service.audits(id));}
    private static long userId(HttpServletRequest r){Object v=r.getAttribute(AuthInterceptor.ATTR_USER_ID);if(v instanceof Number n)return n.longValue();throw new AuthException(401,"未登录");}
}
