package com.bvb.customermodule.repository;

import com.bvb.customermodule.domain.CustomerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;


@Repository
public interface CustomerTypeRepository extends JpaRepository<CustomerType, UUID>
{
    Optional<CustomerType> findByTypeName(CustomerType.TypeName typeName);
}
