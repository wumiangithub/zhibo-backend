package com.zhibo.admin.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HrUimsyhgwxxRepository extends JpaRepository<HrUimsyhgwxxEntity, Long> {

    /** 某部门在职岗位的去重 yhid（顺序稳定便于分页）。 */
    @Query("SELECT DISTINCT g.yhid FROM HrUimsyhgwxxEntity g "
            + "WHERE g.rsStatus = 1 AND g.bmid = :bmid ORDER BY g.yhid ASC")
    List<Long> findDistinctYhidByBmid(@Param("bmid") Long bmid);

    /** 全部在职岗位的去重 yhid。 */
    @Query("SELECT DISTINCT g.yhid FROM HrUimsyhgwxxEntity g "
            + "WHERE g.rsStatus = 1 ORDER BY g.yhid ASC")
    List<Long> findDistinctYhidAll();

    @Query("SELECT g FROM HrUimsyhgwxxEntity g WHERE g.rsStatus = 1 AND g.yhid IN :yhids")
    List<HrUimsyhgwxxEntity> findActiveByYhidIn(@Param("yhids") Collection<Long> yhids);
}
