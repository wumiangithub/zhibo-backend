package com.zhibo.admin.userdata.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActivityUserDataResponse {

    private String id;
    private Summary summary = new Summary();
    private List<Row> rows = new ArrayList<>();
    private TrendDefault trendDefault = new TrendDefault();

    @Data
    public static class Summary {
        private long visitorCount;
        private long viewCount;
        private long avgStayDurationSec;
    }

    @Data
    public static class Row {
        /** live_replay | live | replay */
        private String type;
        private long viewerCount;
        private long viewCount;
        private long avgWatchDurationSec;
    }

    @Data
    public static class TrendDefault {
        private String startTime;
        private String endTime;
    }
}
