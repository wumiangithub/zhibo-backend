package com.zhibo.admin.livereport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class LiveReportDtos {
    private LiveReportDtos() {}
    public record PlanTime(@NotBlank String startTime, @NotBlank String endTime) {}
    public record Attachment(@NotBlank String name, @NotBlank String url, @NotBlank String objectKey,
                             @NotNull Long fileSize, String mimeType) {}
    /** departmentName：现查主播员工部门，无则省略/null */
    public record Host(Long id, String userName, String avatar, String departmentName) {}
    public record SaveRequest(@NotBlank @Size(max=64) String title,
            @NotBlank @Size(max=500) String content, @NotNull @Size(min=1,max=10) List<PlanTime> planTimes,
            @NotBlank String coverUrl, @NotBlank String coverObjectKey, Long coverFileSize,
            String coverMimeType, String coverMaterialId, @NotNull Long hostId,
            @NotNull @Size(min=1,max=20) List<Attachment> attachments) {}
    public record UpdateRequest(String title, String content, List<PlanTime> planTimes,
            String coverUrl, String coverObjectKey, Long coverFileSize, String coverMimeType,
            String coverMaterialId, Long hostId, List<Attachment> attachments) {}
    public record AuditRequest(@NotBlank String result, @Size(max=500) String reason) {}
    public record Result(String id, String status) {}
    /** 软删成功响应。 */
    public record DeleteResult(String id) {}
    public record Detail(String id, String title, String content, List<PlanTime> planTimes,
            String coverUrl, String coverObjectKey, Long coverFileSize, String coverMimeType,
            String coverMaterialId, Host host, List<Attachment> attachments,
            String status, int boundLiveCount, Long submitterId, String submitterName,
            String departmentName, String submittedTime, String updatedTime) {}
    public record ListResult(int page, int pageSize, long total, List<Detail> list) {}
    public record AuditItem(String id, String action, String result, String reason,
            Long operatorId, String operatorName, String departmentName, String createdTime) {}
    public record AuditList(List<AuditItem> list) {}
}
