package com.zhibo.dict;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDictDataRepository extends JpaRepository<SysDictDataEntity, Long> {

    List<SysDictDataEntity> findByDictIdAndStatusOrderByDispIndexAscIdAsc(Long dictId, Integer status);
}
