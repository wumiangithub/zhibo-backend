package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordListResponse {

    private long total;
    private List<ActivityRecordItem> list = new ArrayList<>();
}
