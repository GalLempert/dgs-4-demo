package com.example.company.dal;

import com.example.infrastructure.replication.ResourceRepository;
import com.example.company.domain.Company;
import org.springframework.stereotype.Repository;

/**
 * Nothing to declare: filtering runs through {@code JpaSpecificationExecutor} and the
 * replication feed queries are inherited from {@link ResourceRepository}.
 */
@Repository
public interface CompanyRepository extends ResourceRepository<Company> {
}
