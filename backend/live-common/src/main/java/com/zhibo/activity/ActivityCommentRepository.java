package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityCommentRepository extends JpaRepository<ActivityCommentEntity, Long> {

    Optional<ActivityCommentEntity> findByActivityIdAndProviderMsgId(String activityId, String providerMsgId);

    Page<ActivityCommentEntity> findByActivityIdOrderByDeletedTimeAscCommentTimeDesc(
            String activityId, Pageable pageable);

    Page<ActivityCommentEntity> findByActivityIdAndNicknameContainingOrderByDeletedTimeAscCommentTimeDesc(
            String activityId, String nickname, Pageable pageable);

    @Query(
            "SELECT c.viewerId, COUNT(c) FROM ActivityCommentEntity c "
                    + "WHERE c.activityId = :activityId AND c.deletedTime IS NULL "
                    + "AND c.viewerId IN :viewerIds GROUP BY c.viewerId")
    List<Object[]> countActiveByViewerIds(
            @Param("activityId") String activityId, @Param("viewerIds") Collection<Long> viewerIds);

    long countByActivityIdAndViewerIdAndDeletedTimeIsNull(String activityId, Long viewerId);
}
