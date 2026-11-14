package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ConsoleSessionInviterListResponse {

    private String id;
    private List<ConsoleSessionInviterItem> list = new ArrayList<>();
}
