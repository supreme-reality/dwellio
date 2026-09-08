package com.dwellio.api.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMembershipRepository extends JpaRepository<PropertyMembershipEntity, UUID> {

    Optional<PropertyMembershipEntity> findByOrganizationMembershipIdAndPropertyId(
            UUID organizationMembershipId,
            UUID propertyId
    );

    Optional<PropertyMembershipEntity> findByIdAndPropertyId(UUID id, UUID propertyId);

    List<PropertyMembershipEntity> findByOrganizationMembershipId(UUID organizationMembershipId);

    void deleteByOrganizationMembershipId(UUID organizationMembershipId);

    @Query("""
            select pm from PropertyMembershipEntity pm
            where pm.propertyId = :propertyId
            order by pm.createdAt asc
            """)
    List<PropertyMembershipEntity> findByPropertyIdOrderByCreatedAtAsc(@Param("propertyId") UUID propertyId);

    @Query("""
            select case when count(pm) > 0 then true else false end
            from PropertyMembershipEntity pm
            join OrganizationMembershipEntity om on om.id = pm.organizationMembershipId
            where pm.propertyId = :propertyId
              and om.userId = :userId
              and om.status = 'ACTIVE'
            """)
    boolean existsActiveManagerAssignment(
            @Param("propertyId") UUID propertyId,
            @Param("userId") UUID userId
    );

    @Query("""
            select case when count(pm) > 0 then true else false end
            from PropertyMembershipEntity pm
            join OrganizationMembershipEntity om on om.id = pm.organizationMembershipId
            where om.userId = :userId
              and om.status = 'ACTIVE'
              and om.organizationId = :organizationId
            """)
    boolean existsActiveManagerAssignmentInOrg(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );
}
