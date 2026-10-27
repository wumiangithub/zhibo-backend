package com.zhibo.admin.viewer.dto;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class UpdateViewerTagsRequest {

    /** 要保留的手动标签 id；省略非法；空数组=清空手动标签。 */
    @NotNull
    private List<Long> tagIds = new ArrayList<>();
}
