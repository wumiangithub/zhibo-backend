package com.zhibo.live.org;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HrEmployeePostRepository extends JpaRepository<HrEmployeePostEntity, Long> {

    /** 在职岗位；调用方再按 id 最小取一条。 */
    @Query(
            "select p from HrEmployeePostEntity p where p.rsStatus = 1 and p.yhid = :yhid"
                    + " and p.bmid is not null and p.bmid > 0")
    List<HrEmployeePostEntity> findActiveWithDeptByYhid(@Param("yhid") long yhid);
}
