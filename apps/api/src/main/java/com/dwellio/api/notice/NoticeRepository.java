package com.dwellio.api.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoticeRepository extends JpaRepository<NoticeEntity, UUID> {

    List<NoticeEntity> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId);
}
