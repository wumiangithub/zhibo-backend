package com.zhibo.material;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialGroupRepository extends JpaRepository<MaterialGroupEntity, String> {

    /** 默认分组在前，再按 sortOrder 升序。 */
    List<MaterialGroupEntity> findByTypeOrderByDefaultGroupDescSortOrderAscCreatedTimeAsc(String type);

    Optional<MaterialGroupEntity> findByTypeAndName(String type, String name);

    boolean existsByTypeAndName(String type, String name);
}
