package com.dwellio.api.rent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RentConfigRepository extends JpaRepository<RentConfigEntity, UUID> {

    List<RentConfigEntity> findByPropertyIdOrderByEffectiveFromAscCreatedAtAsc(UUID propertyId);

    @Query("""
            select r from RentConfigEntity r
            where r.propertyId = :propertyId
              and r.roomId is null and r.bedId is null
            """)
    List<RentConfigEntity> findPropertyLevel(@Param("propertyId") UUID propertyId);

    @Query("""
            select r from RentConfigEntity r
            where r.propertyId = :propertyId
              and r.roomId = :roomId and r.bedId is null
            """)
    List<RentConfigEntity> findRoomLevel(@Param("propertyId") UUID propertyId, @Param("roomId") UUID roomId);

    @Query("""
            select r from RentConfigEntity r
            where r.propertyId = :propertyId
              and r.bedId = :bedId
            """)
    List<RentConfigEntity> findBedLevel(@Param("propertyId") UUID propertyId, @Param("bedId") UUID bedId);

    @Query("""
            select r from RentConfigEntity r
            where r.propertyId = :propertyId
              and r.roomId is null and r.bedId is null
              and r.effectiveFrom <= :date
              and (r.effectiveTo is null or r.effectiveTo > :date)
            order by r.effectiveFrom desc
            """)
    List<RentConfigEntity> findPropertyEffective(
            @Param("propertyId") UUID propertyId,
            @Param("date") LocalDate date
    );

    @Query("""
            select r from RentConfigEntity r
            where r.propertyId = :propertyId
              and r.roomId = :roomId and r.bedId is null
              and r.effectiveFrom <= :date
              and (r.effectiveTo is null or r.effectiveTo > :date)
            order by r.effectiveFrom desc
            """)
    List<RentConfigEntity> findRoomEffective(
            @Param("propertyId") UUID propertyId,
            @Param("roomId") UUID roomId,
            @Param("date") LocalDate date
    );

    @Query("""
            select r from RentConfigEntity r
            where r.propertyId = :propertyId
              and r.bedId = :bedId
              and r.effectiveFrom <= :date
              and (r.effectiveTo is null or r.effectiveTo > :date)
            order by r.effectiveFrom desc
            """)
    List<RentConfigEntity> findBedEffective(
            @Param("propertyId") UUID propertyId,
            @Param("bedId") UUID bedId,
            @Param("date") LocalDate date
    );
}
