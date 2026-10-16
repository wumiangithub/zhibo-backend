package com.zhibo.liverole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveRoleMenuRepository extends JpaRepository<LiveRoleMenuEntity, Long> {

    List<LiveRoleMenuEntity> findByRoleIdAndRsStatus(Long roleId, Integer rsStatus);

    void deleteByRoleId(Long roleId);
}
