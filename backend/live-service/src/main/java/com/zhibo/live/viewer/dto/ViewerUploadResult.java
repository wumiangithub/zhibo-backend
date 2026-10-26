package com.zhibo.live.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与直传 TOS 结束后调用方拿到的结果一致：对象键、访问地址、桶上 Location。
 * 有 CDN 时 url 是 CDN + objectKey；未配 CDN 时 url 与 location 相同。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewerUploadResult {

    /** TOS 对象键，如 viewer/avatar/9_171.jpg。 */
    private String objectKey;

    /** 业务落库用的访问地址。 */
    private String url;

    /** 对象在桶上的地址，对应直传 SDK 的 Location。 */
    private String location;
}
