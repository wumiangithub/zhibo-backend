package com.zhibo.admin.activity.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityDetailResponse extends ActivitySummary {

    private String providerRoomId;
    /** 兼容字段：仅 vhall 时等于 providerRoomId */
    private String vhallWebinarId;
    private String introduction;
    private String endTime;
    private Integer verify;
    private Integer pv;
    private String fullEmbedShareLink;
    private Boolean ultraLowLatency;
    /** type=4：是否定时；非录播可空 */
    private Boolean isTiming;
    private Boolean virtualAvatar;
    private String warmupType;
    private String warmupUrl;
    private String promoCoverUrl;
    private String detailHtml;

    private Boolean autoList;
    /** 绑定的直播报备 ID；未绑定不输出 */
    private String reportId;
    /** 绑定报备标题，供编辑页只读回显 */
    private String reportTitle;
    private List<ActivityPerson> hosts;
    private List<ActivityCoursewareView> coursewares;

    private Boolean sellAlone;
    private BigDecimal price;
    private BigDecimal linePrice;
    private String accessValidityType;
    private Integer accessValidityDays;
    /** 仅 encrypted 时回显解密明文 */
    private String password;

    private Boolean playbackEnabled;
    private String playbackValidityType;
    private Integer playbackValidityDays;
    private Boolean playbackSpeedAllow;
    private Boolean playbackFastForwardAllow;
}
