package com.dwellio.api.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<PropertyEntity, UUID> {

    @Query("""
            select p from PropertyEntity p
            where p.organizationId = :organizationId and p.status = 'ACTIVE'
            order by p.createdAt asc
            """)
    List<PropertyEntity> findActiveByOrganizationId(@Param("organizationId") UUID organizationId);

    @Query("""
            select p from PropertyEntity p
            where p.id = :propertyId and p.status = 'ACTIVE'
            """)
    Optional<PropertyEntity> findActiveById(@Param("propertyId") UUID propertyId);

    @Query("""
            select p from PropertyEntity p
            where p.organizationId = :organizationId
              and p.status = 'ACTIVE'
              and p.id in (
                select pm.propertyId from PropertyMembershipEntity pm
                join OrganizationMembershipEntity om on om.id = pm.organizationMembershipId
                where om.userId = :userId and om.status = 'ACTIVE' and om.organizationId = :organizationId
              )
            order by p.createdAt asc
            """)
    List<PropertyEntity> findActiveAssignedToUser(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );
}
