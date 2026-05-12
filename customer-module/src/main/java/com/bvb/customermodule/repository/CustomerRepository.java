package com.bvb.customermodule.repository;

import com.bvb.customermodule.domain.Customer;
import com.bvb.customermodule.domain.CustomerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>,
        JpaSpecificationExecutor<Customer> {

    Optional<Customer> findById (UUID id);

    boolean existsCustomerByEmail(String email);
    boolean existsCustomerByNationalId(String nationalId);
    boolean existsCustomerByPhone(String phone);

    boolean existsCustomerByPhoneAndIdNot(String phone, UUID id);

    // Stats
    @Query("SELECT c.city, COUNT(c) FROM Customer c GROUP BY c.city ORDER BY COUNT(c) DESC")
    List<Object[]> countCustomersByCity();

    @Query("SELECT c.customerType.typeName, COUNT(c) FROM Customer c GROUP BY c.customerType.typeName")
    List<Object[]> countByCustomerType();

    // Search — used by service via searchByName and findByCity
    @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Customer> searchByName(String name, Pageable pageable);

    Page<Customer> findByCity(String city, Pageable pageable);

    @Modifying
    @Query("UPDATE Customer c SET c.email = :email, c.address = :address, c.city = :city, c.phone = :phone, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id = :id")
    void updateCustomerInfo(UUID id, String email, String address, String city, String phone);
}
