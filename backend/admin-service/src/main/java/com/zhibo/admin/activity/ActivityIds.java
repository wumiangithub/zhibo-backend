package com.zhibo.admin.activity;

import com.zhibo.common.UuidIds;

final class ActivityIds {

    private ActivityIds() {
    }

    static String requireUuid(String id) {
        return UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
    }
}
