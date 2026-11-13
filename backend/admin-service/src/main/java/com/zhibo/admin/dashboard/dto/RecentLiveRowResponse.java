package com.zhibo.admin.dashboard.dto;

import lombok.Data;

/**
 * 工作台「最近直播数据」一行。
 * 开播/结束为实际首开～末结；观看指标口径见 API约定 dashboard/recent-lives。
 */
@Data
public class RecentLiveRowResponse {

    /** 本站活动 UUID */
    private String id;

    private String title;

    /** 详情封面；可空 */
    private String coverUrl;

    /** 实际首开，Asia/Shanghai，yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /**
     * 实际末结，Asia/Shanghai，yyyy-MM-dd HH:mm:ss；
     * 直播中尚未闭合时为空串。
     */
    private String endTime;

    /** 1 直播 / 2 预告 / 3 结束 / 4 点播 / 5 回放 */
    private int state;

    /** 直播间浏览量 ≡ 用户数据 live 行观看人次 */
    private long roomViewCount;

    /** 直播间访问人数 ≡ 用户数据 live 行观看人数 */
    private long roomVisitorCount;

    /** 累计观看人次 ≡ 用户数据 live_replay 行观看人次（直播+回放） */
    private long watchViewCount;

    /** 累计观看人数 ≡ 用户数据 live_replay 行观看人数 */
    private long watchViewerCount;

    /** 人均观看时长（整秒）≡ 用户数据 live 行人均 */
    private long avgWatchDurationSec;

    /** 场次窗内 presence 扫峰值（只计观众） */
    private int maxOnlinePeak;

    /**
     * 当日新增：本场观众 ∩ viewer.created_time 落在开播自然日（Asia/Shanghai）。
     */
    private long newUserCount;
}
