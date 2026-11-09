package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 本场观众业务员：优先员工邀请人，否则导入人；皆无则 {@code salesmanId} 为 null。
 */
@Data
@AllArgsConstructor
public class AudienceSalesmanResponse {

    /** 员工 {@code sys_useraccount.rs_mainkey_id}；无则 null。 */
    private Long salesmanId;
}
