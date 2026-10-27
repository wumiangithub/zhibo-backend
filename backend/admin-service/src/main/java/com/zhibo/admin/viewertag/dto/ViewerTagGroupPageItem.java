package com.zhibo.admin.viewertag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerTagGroupPageItem {

    private Long id;
    private String name;
    @JsonProperty("isDefault")
    private boolean isDefault;
    private long tagCount;
}
