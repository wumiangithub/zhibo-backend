package com.zhibo.admin.viewer.dto;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AppendViewerTagsRequest {

    @NotNull
    private List<String> usernames = new ArrayList<>();

    @NotNull
    private List<Long> tagIds = new ArrayList<>();
}
