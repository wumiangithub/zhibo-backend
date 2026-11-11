package com.zhibo.dict;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDictTypeRepository extends JpaRepository<SysDictTypeEntity, Long> {

    Optional<SysDictTypeEntity> findByDictDmAndStatus(String dictDm, Integer status);
}
