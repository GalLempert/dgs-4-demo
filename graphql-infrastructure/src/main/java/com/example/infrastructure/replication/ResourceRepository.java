package com.example.infrastructure.replication;

import com.example.infrastructure.persistence.BaseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Base repository for replicated resources. A domain module's repository only extends
 * this interface - the replication feed queries come for free, resolved by Spring Data
 * against the concrete entity type ({@code #{#entityName}} expands to it):
 *
 * <pre>{@code
 * public interface CompanyRepository extends ResourceRepository<Company> { }
 * }</pre>
 */
@NoRepositoryBean
public interface ResourceRepository<E extends BaseEntity>
        extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    /** The replication feed page: next rows strictly after the given sequence. */
    List<E> findBySequenceGreaterThanOrderBySequenceAsc(long sequence, Pageable pageable);

    /** Highest replication sequence in the table, 0 when the table is empty. */
    @Query("select coalesce(max(e.sequence), 0) from #{#entityName} e")
    long maxSequence();
}
