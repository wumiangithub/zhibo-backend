package com.zhibo.viewer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ViewerTagGroupRepository
        extends JpaRepository<ViewerTagGroupEntity, Long>, JpaSpecificationExecutor<ViewerTagGroupEntity> {

    List<ViewerTagGroupEntity> findByDeletedTimeIsNullOrderBySortOrderAscIdAsc();

    Optional<ViewerTagGroupEntity> findByIdAndDeletedTimeIsNull(Long id);

    Optional<ViewerTagGroupEntity> findFirstByIsDefaultAndDeletedTimeIsNull(int isDefault);

    boolean existsByNameAndDeletedTimeIsNull(String name);

    boolean existsByNameAndDeletedTimeIsNullAndIdNot(String name, Long id);

    @Query("select coalesce(max(g.sortOrder), 0) from ViewerTagGroupEntity g where g.deletedTime is null")
    int findMaxSortOrderAmongActive();
}
