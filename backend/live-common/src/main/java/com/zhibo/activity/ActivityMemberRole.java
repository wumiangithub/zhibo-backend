package com.zhibo.activity;

/**
 * 本场开播角色。
 * <p>
 * 库列 {@code activity_member.role}、接口 JSON 的 {@code role} 均为本枚举的整数值。
 * <b>与微吼 {@code GET /v3/webinars/live/get-role-url} 的 {@code type} 一一对应</b>
 *（1 主持人/主播、2 嘉宾、3 助理），见 https://vhall.apifox.cn/234257715e0 。
 * 不是员工组织里的系统角色（{@code sys_uimsrole}）。
 * 展示名用 {@link #getLabel()}，勿把中文或英文词写入库。
 */
public enum ActivityMemberRole {

    /** 1 = 主播（微吼主持人）；默认标签「讲师」 */
    HOST(1, "主播", "讲师"),
    /** 2 = 嘉宾（微吼嘉宾）；默认标签「嘉宾」 */
    GUEST(2, "嘉宾", "嘉宾"),
    /** 3 = 助理（微吼助理）；默认标签「助教」 */
    ASSISTANT(3, "助理", "助教");

    private final int code;
    private final String label;
    private final String defaultTag;

    ActivityMemberRole(int code, String label, String defaultTag) {
        this.code = code;
        this.label = label;
        this.defaultTag = defaultTag;
    }

    /** 写入库与接口的整数值；亦为微吼 get-role-url 的 type。 */
    public int getCode() {
        return code;
    }

    /** 管理端展示用中文名。 */
    public String getLabel() {
        return label;
    }

    /** 新加入该角色时的默认标签。 */
    public String getDefaultTag() {
        return defaultTag;
    }

    /**
     * 按接口 / 库中的整数解析。
     *
     * @throws IllegalArgumentException 非法数字
     */
    public static ActivityMemberRole requireCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("开播角色无效");
        }
        for (ActivityMemberRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("开播角色无效");
    }

    public static boolean isHost(int code) {
        return code == HOST.code;
    }
}
