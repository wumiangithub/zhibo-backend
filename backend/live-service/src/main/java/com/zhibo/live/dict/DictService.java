package com.zhibo.live.dict;

import com.zhibo.dict.SysDictDataEntity;
import com.zhibo.dict.SysDictService;
import com.zhibo.live.dict.dto.DictDataItem;
import com.zhibo.live.dict.dto.DictDataResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictService sysDictService;

    public DictDataResponse listByDictDm(String dictDm) {
        SysDictService.DictBundle bundle = sysDictService.findByDictDm(dictDm);
        if (bundle == null) {
            throw new IllegalArgumentException("字典不存在");
        }
        DictDataResponse response = new DictDataResponse();
        response.setDictDm(bundle.dictDm());
        response.setDictName(bundle.dictName());
        for (SysDictDataEntity row : bundle.items()) {
            if (row.getDictLabel() == null || row.getDictLabel().isBlank()) {
                continue;
            }
            if (row.getDictValue() == null) {
                continue;
            }
            DictDataItem item = new DictDataItem();
            item.setLabel(row.getDictLabel().trim());
            item.setValue(row.getDictValue());
            response.getList().add(item);
        }
        return response;
    }
}
