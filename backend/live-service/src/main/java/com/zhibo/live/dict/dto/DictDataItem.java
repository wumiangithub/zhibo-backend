package com.zhibo.live.dict.dto;

import lombok.Data;

/** 字典项：label=dict_label，value=dict_value。 */
@Data
public class DictDataItem {

    private String label;
    private Integer value;
}
