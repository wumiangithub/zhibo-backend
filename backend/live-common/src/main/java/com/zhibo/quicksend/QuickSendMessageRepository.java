package com.zhibo.quicksend;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuickSendMessageRepository extends JpaRepository<QuickSendMessageEntity, String> {

    long countByGroupIdAndDeletedTimeIsNull(String groupId);

    Page<QuickSendMessageEntity> findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(
            String groupId, Pageable pageable);

    List<QuickSendMessageEntity> findByGroupIdAndDeletedTimeIsNullOrderBySortOrderAscIdAsc(
            String groupId);

    Optional<QuickSendMessageEntity> findByIdAndDeletedTimeIsNull(String id);

    List<QuickSendMessageEntity> findByIdInAndDeletedTimeIsNull(Collection<String> ids);
}
