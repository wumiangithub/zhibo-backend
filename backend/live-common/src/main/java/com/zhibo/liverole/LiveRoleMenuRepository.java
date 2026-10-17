package com.zhibo.liverole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LiveRoleMenuRepository extends JpaRepository<LiveRoleMenuEntity, Long> {

    List<LiveRoleMenuEntity> findByRoleIdAndRsStatus(Long roleId, Integer rsStatus);

    /**
     * 立刻发 DELETE。派生 delete 会先装入持久化上下文，flush 时 Hibernate 先 INSERT 再 DELETE，
     * 会撞上 uk_live_role_menu (role_id, menu_code)。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM LiveRoleMenuEntity m WHERE m.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
