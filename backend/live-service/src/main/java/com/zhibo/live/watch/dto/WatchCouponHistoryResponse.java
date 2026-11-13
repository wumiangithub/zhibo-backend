package com.zhibo.live.watch.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchCouponHistoryResponse {

    private List<WatchCouponHistoryItem> list = new ArrayList<>();
}
