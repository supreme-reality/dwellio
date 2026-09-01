package com.dwellio.api.movein;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MoveInServiceSelectionRepository extends JpaRepository<MoveInServiceSelectionEntity, UUID> {

    List<MoveInServiceSelectionEntity> findByMoveInId(UUID moveInId);

    void deleteByMoveInId(UUID moveInId);
}
