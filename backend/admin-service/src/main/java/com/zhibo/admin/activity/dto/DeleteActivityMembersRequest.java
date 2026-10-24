package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 按员工 id 删除本场开播成员。只删列出的人，其余不动。
 */
@Data
public class DeleteActivityMembersRequest {

    /** 要删除的员工 rs_mainkey_id */
    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
