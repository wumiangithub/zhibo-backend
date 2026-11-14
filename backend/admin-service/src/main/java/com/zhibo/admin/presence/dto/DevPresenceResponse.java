package com.zhibo.admin.presence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevPresenceResponse {

    private int ok;
    private int failed;
    private List<DevPresenceItemResult> items;
}
