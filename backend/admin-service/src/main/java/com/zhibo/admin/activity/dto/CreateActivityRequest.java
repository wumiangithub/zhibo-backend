package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建活动请求（本站落库 + 按 provider 调云侧）。
 */
@Data
public class CreateActivityRequest {

    /** 直播服务商：vhall / aliyun / tencent */
    @NotBlank(message = "provider 不能为空")
    private String provider;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题不能超过64个字")
    private String title;

    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /** 结束时间（可选）；只落本站，须晚于 startTime */
    private String endTime;

    @Size(max = 256, message = "简介不能超过256个字")
    private String introduction;

    /**
     * 1 音频 / 2 视频 / 3 互动 / 4 录播（微吼走 create-demand）。
     */
    @NotNull(message = "直播类型不能为空")
    @Min(value = 1, message = "直播类型无效")
    @Max(value = 4, message = "直播类型无效")
    private Integer type;

    /** type=4 且 provider=vhall 时必填 */
    private String recordId;

    private String viewerStyle;
    private Boolean ultraLowLatency;
    private Boolean virtualAvatar;
    private String coverUrl;
    private String warmupType;
    private String warmupUrl;
    private String promoCoverUrl;

    @Size(max = 100000, message = "详情过长")
    private String detailHtml;

    /** true 立即上架；false 待上架（暂不上架、定时上架都传 false）。 */
    @NotNull(message = "listed 不能为空")
    private Boolean listed;

    /** free / paid / encrypted / specified。有效期固定「长期有效」。 */
    @NotBlank(message = "sellType 不能为空")
    @Pattern(regexp = "free|paid|encrypted|specified", message = "sellType 无效")
    private String sellType;

    /** 员工 rs_mainkey_id 的十进制字符串。 */
    private List<String> ownerIds;

    /** 关联商品快照。 */
    private List<ProductBinding> products;

    @Data
    public static class ProductBinding {
        private String spuId;
        private String productName;
    }
}
