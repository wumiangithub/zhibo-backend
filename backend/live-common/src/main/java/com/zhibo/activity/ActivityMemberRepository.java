package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActivityMemberRepository extends JpaRepository<ActivityMemberEntity, ActivityMemberId> {

    @Query("""
            SELECT m FROM ActivityMemberEntity m
            WHERE m.id.activityId IN :ids
            ORDER BY m.id.activityId ASC, m.role ASC, m.sortOrder ASC
            """)
    List<ActivityMemberEntity> findByActivityIds(@Param("ids") Collection<String> ids);

    void deleteByIdActivityId(String activityId);

    /**
     * 先 flush 再删，且不要 clear 会话：新建活动时若 clear，会丢掉尚未落库的 {@code activity}，
     * 随后插入 {@code activity_member} 会踩外键失败。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM ActivityMemberEntity m WHERE m.id.activityId = :activityId AND m.role = :role")
    void deleteByActivityIdAndRole(@Param("activityId") String activityId, @Param("role") int role);
}
