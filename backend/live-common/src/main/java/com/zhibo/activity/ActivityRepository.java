package com.zhibo.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Optional<ActivityEntity> findByProviderAndProviderRoomId(String provider, String providerRoomId);

    /** 口令开播：按唯一口令查未软删活动。 */
    Optional<ActivityEntity> findByHostPasscodeAndDeletedTimeIsNull(String hostPasscode);

    boolean existsByHostPasscode(String hostPasscode);

    /** 录播转码回调：挂该素材且仍未就绪的活动。 */
    List<ActivityEntity> findByRecordMaterialIdAndReadyAndDeletedTimeIsNull(
            String recordMaterialId, Integer ready);
}
