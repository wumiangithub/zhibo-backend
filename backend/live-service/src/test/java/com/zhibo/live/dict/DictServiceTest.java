package com.zhibo.live.dict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.zhibo.dict.SysDictDataEntity;
import com.zhibo.dict.SysDictService;
import com.zhibo.live.dict.dto.DictDataResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock
    private SysDictService sysDictService;

    private DictService dictService;

    @BeforeEach
    void setUp() {
        dictService = new DictService(sysDictService);
    }

    @Test
    void listByDictDm_mapsLabelsAndValues() {
        SysDictDataEntity a = new SysDictDataEntity();
        a.setDictLabel("wx_wap");
        a.setDictValue(4);
        SysDictDataEntity b = new SysDictDataEntity();
        b.setDictLabel("wx_lite");
        b.setDictValue(6);
        when(sysDictService.findByDictDm("pay_channel"))
                .thenReturn(new SysDictService.DictBundle("pay_channel", "支付渠道", List.of(a, b)));

        DictDataResponse res = dictService.listByDictDm("pay_channel");
        assertEquals("pay_channel", res.getDictDm());
        assertEquals("支付渠道", res.getDictName());
        assertEquals(2, res.getList().size());
        assertEquals("wx_wap", res.getList().get(0).getLabel());
        assertEquals(4, res.getList().get(0).getValue());
        assertEquals("wx_lite", res.getList().get(1).getLabel());
        assertEquals(6, res.getList().get(1).getValue());
    }

    @Test
    void listByDictDm_missing_throws() {
        when(sysDictService.findByDictDm("nope")).thenReturn(null);
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> dictService.listByDictDm("nope"));
        assertEquals("字典不存在", ex.getMessage());
    }
}
