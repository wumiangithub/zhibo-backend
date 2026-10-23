package com.zhibo.live.viewer;

/** 手机号脱敏。接口只出这个，不出明文。 */
final class ViewerPhoneMask {

    private ViewerPhoneMask() {
    }

    static String mask(String phone) {
        if (phone == null) {
            return null;
        }
        String value = phone.trim();
        int length = value.length();
        if (length == 0) {
            return null;
        }
        if (length <= 4) {
            return "****";
        }
        if (length == 11) {
            return value.substring(0, 3) + "****" + value.substring(length - 4);
        }
        return value.substring(0, 2) + "****" + value.substring(length - 2);
    }
}
