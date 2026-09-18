package com.dwellio.api.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembershipEntity, UUID> {

    @Query("""
            select m from OrganizationMembershipEntity m
            where m.userId = :userId and m.status = 'ACTIVE'
            order by m.createdAt asc
            """)
    List<OrganizationMembershipEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            select m from OrganizationMembershipEntity m
            where m.organizationId = :organizationId
              and m.userId = :userId
              and m.status = 'ACTIVE'
            """)
    Optional<OrganizationMembershipEntity> findActiveByOrganizationIdAndUserId(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );

    Optional<OrganizationMembershipEntity> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<OrganizationMembershipEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            select m from OrganizationMembershipEntity m
            where m.organizationId = :organizationId
            order by m.createdAt asc
            """)
    List<OrganizationMembershipEntity> findByOrganizationIdOrderByCreatedAtAsc(
            @Param("organizationId") UUID organizationId
    );
}
