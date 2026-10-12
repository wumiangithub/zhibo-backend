package com.zhibo.admin.material.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialGroupResponse {
    private String id;
    private String name;
    private long count;
    /** 避免 Jackson 把 boolean isDefault 序列化成 default。 */
    @JsonProperty("isDefault")
    private boolean isDefault;
}
