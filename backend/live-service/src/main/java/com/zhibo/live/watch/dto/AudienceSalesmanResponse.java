package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 本场观众业务员：仅员工邀请人；{@code salesmanId}={@code hr_uimsyhxx.identifier}，
 * {@code salesmanDeptId}={@code hr_uimsyhgwxx.bmid}（在职岗位 id 最小一条）；无则对应字段 null。
 */
@Data
@AllArgsConstructor
public class AudienceSalesmanResponse {

    /** {@code hr_uimsyhxx.identifier}；无邀请人或档案缺失则为 null。 */
    private String salesmanId;

    /** {@code hr_uimsyhgwxx.bmid}；无岗位则为 null（仍可只带 salesmanId）。 */
    private Long salesmanDeptId;
}
