package com.zhibo.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<ActivityEntity, String> {

    /**
     * statesEmpty=true 时不过滤 state；为 false 时 a.state IN :states。
     * states 在不过滤时仍须传非空占位（如 List.of(0)），避免 IN () 语法问题。
     */
    @Query("SELECT a FROM ActivityEntity a WHERE " +
            "a.deletedTime IS NULL AND " +
            "(:provider IS NULL OR a.provider = :provider) AND " +
            "(:statesEmpty = true OR a.state IN :states) AND " +
            "(:keyword IS NULL OR a.title LIKE CONCAT('%', :keyword, '%') OR a.id = :keyword) AND " +
            "(:shelfStatus IS NULL OR a.shelfStatus = :shelfStatus) AND " +
            "(:startFrom IS NULL OR a.startTime >= :startFrom) AND " +
            "(:startTo IS NULL OR a.startTime <= :startTo)")
    Page<ActivityEntity> search(
            @Param("provider") String provider,
            @Param("statesEmpty") boolean statesEmpty,
            @Param("states") Collection<Integer> states,
            @Param("keyword") String keyword,
            @Param("shelfStatus") String shelfStatus,
            @Param("startFrom") String startFrom,
            @Param("startTo") String startTo,
            Pageable pageable);

    Page<ActivityEntity> findAllByOrderByCreatedTimeDesc(Pageable pageable);

    /**
     * 工作台「最近直播」：已有实际开播汇总的未软删活动，按首开时间倒序（全局候选）。
     */
    @Query("SELECT a FROM ActivityEntity a WHERE a.deletedTime IS NULL "
            + "AND a.liveActualStartTime IS NOT NULL "
            + "ORDER BY a.liveActualStartTime DESC")
    Page<ActivityEntity> findRecentWithActualStart(Pageable pageable);

    /**
     * 近 N 自然日窗口内（按 {@code liveActualStartTime}）已开播场次数量。
     */
    @Query("SELECT COUNT(a) FROM ActivityEntity a WHERE a.deletedTime IS NULL "
            + "AND a.liveActualStartTime IS NOT NULL "
            + "AND a.liveActualStartTime >= :from AND a.liveActualStartTime < :to")
    long countWithActualStartBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 近 N 自然日窗口内已开播场次，按首开时间倒序分页。
     */
    @Query("SELECT a FROM ActivityEntity a WHERE a.deletedTime IS NULL "
            + "AND a.liveActualStartTime IS NOT NULL "
            + "AND a.liveActualStartTime >= :from AND a.liveActualStartTime < :to "
            + "ORDER BY a.liveActualStartTime DESC")
    Page<ActivityEntity> findRecentWithActualStartBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    Optional<ActivityEntity> findByProviderAndProviderRoomId(String provider, String providerRoomId);

    /** 口令开播：按唯一口令查未软删活动。 */
    Optional<ActivityEntity> findByHostPasscodeAndDeletedTimeIsNull(String hostPasscode);

    boolean existsByHostPasscode(String hostPasscode);

    /** 录播转码回调：挂该素材且仍未就绪的活动。 */
    List<ActivityEntity> findByRecordMaterialIdAndReadyAndDeletedTimeIsNull(
            String recordMaterialId, Integer ready);

    /**
     * 观众「我的学习」：仅本站有观看权益（{@code activity_audience.entitlement_status=1}）且未软删的场次。
     * {@code statesEmpty=true} 时不过滤 state；否则 {@code a.state IN :states}（不过滤时 states 仍须非空占位）。
     */
    @Query("SELECT a FROM ActivityEntity a WHERE "
            + "a.deletedTime IS NULL AND "
            + "EXISTS (SELECT 1 FROM ActivityAudienceEntity aa WHERE aa.activityId = a.id "
            + "AND aa.username = :username AND aa.entitlementStatus = 1) AND "
            + "(:statesEmpty = true OR a.state IN :states)")
    Page<ActivityEntity> searchEntitledForViewer(
            @Param("username") String viewerUsername,
            @Param("statesEmpty") boolean statesEmpty,
            @Param("states") Collection<Integer> states,
            Pageable pageable);
}
