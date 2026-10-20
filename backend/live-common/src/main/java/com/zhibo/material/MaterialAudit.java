package com.zhibo.material;

/** 素材 AI / 风控审核。库和接口都用整数。 */
public final class MaterialAudit {

    /** 待审核 */
    public static final int PENDING = 0;
    /** 已通过 */
    public static final int PASSED = 1;
    /** 已驳回 */
    public static final int REJECTED = 2;

    private MaterialAudit() {
    }

    public static boolean isPending(Integer status) {
        return status == null || status == PENDING;
    }

    /** AI、风控两审都是已通过。 */
    public static boolean bothPassed(MaterialEntity material) {
        return material != null
                && Integer.valueOf(PASSED).equals(material.getAiAuditStatus())
                && Integer.valueOf(PASSED).equals(material.getRiskAuditStatus());
    }
}
