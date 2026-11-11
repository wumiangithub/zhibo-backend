package com.zhibo.viewer;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewerTagMemberRepository extends JpaRepository<ViewerTagMemberEntity, Long> {

    long countByTagId(Long tagId);

    void deleteByTagId(Long tagId);

    List<ViewerTagMemberEntity> findByUid(Long uid);

    List<ViewerTagMemberEntity> findByUidIn(Collection<Long> uids);

    List<ViewerTagMemberEntity> findByTagIdIn(Collection<Long> tagIds);

    @Query(
            "select count(distinct m.uid) from ViewerTagMemberEntity m"
                    + " where m.tagId in :tagIds and m.uid is not null and m.uid > 0")
    long countDistinctViewersByTagIdIn(@Param("tagIds") Collection<Long> tagIds);

    @Query(
            "select m.tagId as tagId, count(m) as cnt from ViewerTagMemberEntity m"
                    + " where m.tagId in :tagIds group by m.tagId")
    List<TagMemberCount> countGroupedByTagId(@Param("tagIds") Collection<Long> tagIds);

    interface TagMemberCount {
        Long getTagId();

        long getCnt();
    }
}
