package com.zhibo.admin.activity;

import java.util.UUID;

final class ActivityIds {

    private ActivityIds() {
    }

    static String requireUuid(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("活动 id 无效");
        }
        try {
            return UUID.fromString(id.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("活动 id 须为本站 UUID");
        }
    }
}
