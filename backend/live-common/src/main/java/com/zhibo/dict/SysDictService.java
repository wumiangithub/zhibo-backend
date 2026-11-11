package com.zhibo.dict;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统字典只读查询。 */
@Service
@RequiredArgsConstructor
public class SysDictService {

    public static final int STATUS_NORMAL = 1;

    private final SysDictTypeRepository typeRepository;
    private final SysDictDataRepository dataRepository;

    /**
     * 按 {@code dict_dm} 查启用中的类型及数据项。
     * 类型不存在 / 已删 → 空列表（不抛）；调用方可自行 400。
     */
    @Transactional(readOnly = true)
    public DictBundle findByDictDm(String dictDm) {
        String dm = dictDm == null ? "" : dictDm.trim();
        if (dm.isEmpty()) {
            return null;
        }
        return typeRepository.findByDictDmAndStatus(dm, STATUS_NORMAL)
                .map(type -> new DictBundle(
                        type.getDictDm(),
                        type.getDictName(),
                        dataRepository.findByDictIdAndStatusOrderByDispIndexAscIdAsc(
                                type.getId(), STATUS_NORMAL)))
                .orElse(null);
    }

    public record DictBundle(String dictDm, String dictName, List<SysDictDataEntity> items) {}
}
