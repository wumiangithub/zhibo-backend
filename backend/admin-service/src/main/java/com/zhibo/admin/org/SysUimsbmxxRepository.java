package com.zhibo.admin.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SysUimsbmxxRepository extends JpaRepository<SysUimsbmxxEntity, Long> {

    @Query("SELECT d FROM SysUimsbmxxEntity d WHERE d.rsStatus = 1 AND d.qybz = 1 ORDER BY d.bmbm ASC")
    List<SysUimsbmxxEntity> findActiveEnabled();
}
