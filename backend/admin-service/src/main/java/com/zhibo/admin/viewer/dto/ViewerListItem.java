package com.zhibo.admin.viewer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewerListItem {

    private Long id;
    private String nickname;
    private String avatarUrl;
    /** 1 微信 / 2 导入 */
    private int registerChannel;
    private String phoneMasked;
    /** 1 正常 / 0 停用（与 banAccess 同步） */
    private int status;
    /** 禁止评论 0/1 */
    private int banComment;
    /** 禁止购买 0/1 */
    private int banPurchase;
    /** 禁止访问 0/1 */
    private int banAccess;
    private String createdTime;
    private String lastVisitTime;
    private long consumeCount;
    private String consumeAmount = "0.00";
    private long totalLearnDurationSec;
    private long todayLearnDurationSec;
    /** 完成课程数；本轮恒 0 */
    private long completedCourseCount;
    /** 最近购买商品名；本轮省略 */
    private String recentPurchaseProduct;
    /** 首次支付时间；本轮省略 */
    private String firstPayTime;
    /** 标签 id（含自动，按打标时间） */
    private List<Long> tagIds = new ArrayList<>();
    /** 标签名（与 tagIds 同序） */
    private List<String> tagLabels = new ArrayList<>();
}
