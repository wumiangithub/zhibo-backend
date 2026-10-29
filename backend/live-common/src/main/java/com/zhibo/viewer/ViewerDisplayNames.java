package com.zhibo.viewer;

/**
 * 观众展示昵称（API约定「通则 · 观众展示昵称」）：
 * 有昵称→昵称；无昵称有手机→{@code 用户} + 尾号 4 位；否则→{@code 用户_} + id。
 * admin / live 共用，禁止各写一套。
 */
public final class ViewerDisplayNames {

    private ViewerDisplayNames() {
    }

    public static String displayNickname(ViewerEntity viewer) {
        if (viewer == null) {
            return "用户_0";
        }
        String nick = viewer.getNickname();
        if (nick != null && !nick.isBlank()) {
            return nick.trim();
        }
        String phone = viewer.getPhone();
        if (phone != null) {
            String digits = phone.trim();
            if (!digits.isEmpty()) {
                String tail = digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
                return "用户" + tail;
            }
        }
        Long id = viewer.getId();
        return "用户_" + (id != null ? id : 0);
    }

    /** 无实体行时仍给出可展示的兜底（缺库行 / 已删）。 */
    public static String displayNicknameForId(long viewerId) {
        if (viewerId <= 0) {
            return "用户_0";
        }
        return "用户_" + viewerId;
    }
}
