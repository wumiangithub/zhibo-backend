package com.zhibo.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ActivityRepository extends JpaRepository<ActivityEntity, String> {

    @Query("SELECT a FROM ActivityEntity a WHERE " +
            "a.deletedTime IS NULL AND " +
            "(:provider IS NULL OR a.provider = :provider) AND " +
            "(:state IS NULL OR a.state = :state) AND " +
            "(:keyword IS NULL OR a.title LIKE CONCAT('%', :keyword, '%') OR a.id = :keyword) AND " +
            "(:shelfStatus IS NULL OR a.shelfStatus = :shelfStatus) AND " +
            "(:startFrom IS NULL OR a.startTime >= :startFrom) AND " +
            "(:startTo IS NULL OR a.startTime <= :startTo) " +
            "ORDER BY a.createdTime DESC")
    Page<ActivityEntity> search(
            @Param("provider") String provider,
            @Param("state") Integer state,
            @Param("keyword") String keyword,
            @Param("shelfStatus") String shelfStatus,
            @Param("startFrom") String startFrom,
            @Param("startTo") String startTo,
            Pageable pageable);

    Page<ActivityEntity> findAllByOrderByCreatedTimeDesc(Pageable pageable);

    Optional<ActivityEntity> findByProviderAndProviderRoomId(String provider, String providerRoomId);
}
