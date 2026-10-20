package com.zhibo.admin.activity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActivityMemberListResponse {

    private List<ActivityMemberItem> list = new ArrayList<>();
}
