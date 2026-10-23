package com.zhibo.viewer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewerRepository extends JpaRepository<ViewerEntity, Long> {

    Optional<ViewerEntity> findByPhone(String phone);
}
