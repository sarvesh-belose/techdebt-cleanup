package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select n from Notification n where n.userId = :userId and n.readAt is null order by n.createdAt desc")
    List<Notification> findUnreadByUserId(Long userId);

    @Query("select count(n) from Notification n where n.userId = :userId and n.readAt is null")
    long countUnreadByUserId(Long userId);
}
