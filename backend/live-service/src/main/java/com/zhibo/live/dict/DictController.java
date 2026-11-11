package com.zhibo.live.dict;

import com.zhibo.live.dict.dto.DictDataResponse;
import com.zhibo.live.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统字典只读。公开，不需观众 JWT。
 * 支付页等按 {@code dict_dm} 拉枚举（如 {@code pay_channel}）。
 */
@RestController
@RequestMapping("/api/v1/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/data/{dictDm}")
    public ApiResponse<DictDataResponse> data(@PathVariable String dictDm) {
        return ApiResponse.ok(dictService.listByDictDm(dictDm));
    }
}
