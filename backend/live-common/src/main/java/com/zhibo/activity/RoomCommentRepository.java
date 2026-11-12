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

    /** 回放页问答历史：未软删提问，旧→新。 */
    List<RoomCommentEntity> findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
            String activityId, Integer commentType);

    Page<RoomCommentEntity> findByActivityIdOrderByDeletedTimeAscCommentTimeDesc(
            String activityId, Pageable pageable);

    Page<RoomCommentEntity> findByActivityIdAndNicknameContainingOrderByDeletedTimeAscCommentTimeDesc(
            String activityId, String nickname, Pageable pageable);

    /** 未软删的聊天 + 提问计入「评论次数」（D107）；不计优惠券/图片（D113）。 */
    @Query(
            "SELECT c.username, COUNT(c) FROM RoomCommentEntity c "
                    + "WHERE c.activityId = :activityId AND c.deletedTime IS NULL "
                    + "AND c.commentType IN (1, 2) "
                    + "AND c.username IN :usernames GROUP BY c.username")
    List<Object[]> countActiveByUsernames(
            @Param("activityId") String activityId, @Param("usernames") Collection<String> usernames);

    long countByActivityIdAndUsernameAndDeletedTimeIsNull(String activityId, String username);
}
