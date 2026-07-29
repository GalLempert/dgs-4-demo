package com.example.company.dal;

import com.example.infrastructure.replication.ResourceRepository;
import com.example.company.domain.Company;
import org.springframework.stereotype.Repository;

/**
 * Filtering runs through {@code JpaSpecificationExecutor} and the replication feed
 * queries are inherited from {@link ResourceRepository}; the only declared query backs
 * the name-uniqueness rule (the name is the company's natural key).
 */
@Repository
public interface CompanyRepository extends ResourceRepository<Company> {

    boolean existsByNameIgnoreCase(String name);
}
