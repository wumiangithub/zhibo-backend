package com.zhibo.admin.console.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsoleCouponHistoryResponse {

    private List<ConsoleCouponHistoryItem> list = new ArrayList<>();
}
