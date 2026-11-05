package com.zhibo.activity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomCommentRepository extends JpaRepository<RoomCommentEntity, Long> {

    Optional<RoomCommentEntity> findByActivityIdAndMsgId(String activityId, String msgId);

    Optional<RoomCommentEntity> findByActivityIdAndQuestionId(String activityId, String questionId);

    Page<RoomCommentEntity> findByActivityIdOrderByDeletedTimeAscCommentTimeDesc(
            String activityId, Pageable pageable);

    Page<RoomCommentEntity> findByActivityIdAndNicknameContainingOrderByDeletedTimeAscCommentTimeDesc(
            String activityId, String nickname, Pageable pageable);

    /** 未软删的聊天 + 提问均计入「评论次数」（D107）。 */
    @Query(
            "SELECT c.viewerId, COUNT(c) FROM RoomCommentEntity c "
                    + "WHERE c.activityId = :activityId AND c.deletedTime IS NULL "
                    + "AND c.viewerId IN :viewerIds GROUP BY c.viewerId")
    List<Object[]> countActiveByViewerIds(
            @Param("activityId") String activityId, @Param("viewerIds") Collection<Long> viewerIds);

    long countByActivityIdAndViewerIdAndDeletedTimeIsNull(String activityId, Long viewerId);
}
