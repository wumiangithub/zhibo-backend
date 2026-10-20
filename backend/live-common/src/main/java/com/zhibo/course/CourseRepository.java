package com.zhibo.course;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface CourseRepository extends JpaRepository<CourseEntity, String> {

    @Query("SELECT c FROM CourseEntity c WHERE c.deletedTime IS NULL "
            + "AND (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:shelfStatus IS NULL OR c.shelfStatus = :shelfStatus) "
            + "AND (:riskStatus IS NULL OR c.riskStatus = :riskStatus)")
    Page<CourseEntity> search(
            @Param("keyword") String keyword,
            @Param("shelfStatus") String shelfStatus,
            @Param("riskStatus") String riskStatus,
            Pageable pageable);

    @Query("SELECT c FROM CourseEntity c WHERE c.deletedTime IS NULL AND c.id IN :ids "
            + "AND (:keyword IS NULL OR c.title LIKE CONCAT('%', :keyword, '%')) "
            + "AND (:shelfStatus IS NULL OR c.shelfStatus = :shelfStatus) "
            + "AND (:riskStatus IS NULL OR c.riskStatus = :riskStatus)")
    Page<CourseEntity> searchByIds(
            @Param("ids") Collection<String> ids,
            @Param("keyword") String keyword,
            @Param("shelfStatus") String shelfStatus,
            @Param("riskStatus") String riskStatus,
            Pageable pageable);
}
