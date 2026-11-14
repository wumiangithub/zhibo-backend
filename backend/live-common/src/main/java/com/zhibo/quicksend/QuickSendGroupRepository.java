package com.zhibo.quicksend;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickSendGroupRepository extends JpaRepository<QuickSendGroupEntity, String> {

    List<QuickSendGroupEntity> findAllByOrderByDefaultGroupDescSortOrderAscIdAsc();

    boolean existsByName(String name);
}
