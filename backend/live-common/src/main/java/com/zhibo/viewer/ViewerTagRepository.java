package com.zhibo.viewer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewerTagRepository
        extends JpaRepository<ViewerTagEntity, Long>, JpaSpecificationExecutor<ViewerTagEntity> {

    Optional<ViewerTagEntity> findByIdAndDeletedTimeIsNull(Long id);

    boolean existsByNameAndDeletedTimeIsNull(String name);

    boolean existsByNameAndDeletedTimeIsNullAndIdNot(String name, Long id);

    long countByGroupIdAndDeletedTimeIsNull(Long groupId);

    @Query(
            "select t.groupId as groupId, count(t) as cnt from ViewerTagEntity t"
                    + " where t.deletedTime is null and t.groupId in :groupIds group by t.groupId")
    List<GroupTagCount> countActiveGroupedByGroupId(@Param("groupIds") Collection<Long> groupIds);

    interface GroupTagCount {
        Long getGroupId();

        long getCnt();
    }
}
