package com.zhibo.admin.viewertag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerTagGroupItem {

    private Long id;
    private String name;
    /** 避免 Jackson 把 boolean isDefault 序列化成 default。 */
    @JsonProperty("isDefault")
    private boolean isDefault;
}
