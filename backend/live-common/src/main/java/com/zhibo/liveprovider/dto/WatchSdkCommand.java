package com.zhibo.liveprovider.dto;

/**
 * 观看 / 中控台 SDK 签名入参。
 *
 * @param providerRoomId 云侧房间 id
 * @param nickname       展示昵称（已由调用方截断/默认）
 * @param viewerIdentity 观众 username，或员工数字 id 的字符串（员工场景配合 {@code email}）
 * @param head           头像 URL；有无真实头像都拼 {@code ?v=}（身份同 email 本地部分，D111）。微吼 JSSDK {@code head}；仅非空参与签名
 * @param email          非空则直接用于签名（中控台 {@code z{id}@qkzb.com}，D110）；空则按观众
 *                       {@code {username}@qkzb.com}（D92）。角色进房仍走 get-role-url 的 {@code e{id}}
 */
public record WatchSdkCommand(
        String providerRoomId,
        String nickname,
        String viewerIdentity,
        String head,
        String email
) {
    /** 观众签名：email 由 provider 按 viewerIdentity（username）推导。 */
    public WatchSdkCommand(String providerRoomId, String nickname, String viewerIdentity, String head) {
        this(providerRoomId, nickname, viewerIdentity, head, null);
    }

    /** @deprecated 保留兼容；{@code viewerIdentity} 须为 username 字符串。 */
    @Deprecated
    public WatchSdkCommand(String providerRoomId, String nickname, Long viewerId, String head) {
        this(providerRoomId, nickname, viewerId == null ? null : Long.toString(viewerId), head, null);
    }

    /** @deprecated 用 {@link #viewerIdentity()}。 */
    @Deprecated
    public Long viewerId() {
        if (viewerIdentity == null || viewerIdentity.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(viewerIdentity.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
