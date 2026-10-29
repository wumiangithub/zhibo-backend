package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchRecordItem {

    /** 微吼回放 id，与 admin records list[].id 相同 */
    private Long id;
}
