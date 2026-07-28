package com.example.infrastructure.replication;

import com.example.infrastructure.filter.FilterSpecificationBuilder;
import com.example.infrastructure.filter.QueryResultCap;
import org.springframework.stereotype.Component;

/**
 * The infrastructure collaborators every {@link ResourceDal} needs, bundled into one
 * injectable bean so a domain DAL's constructor stays a two-liner:
 *
 * <pre>{@code
 * public CompanyDal(CompanyRepository repository, ResourceDalSupport support) {
 *     super("Company", "company_replication_seq", repository, support);
 * }
 * }</pre>
 */
@Component
public class ResourceDalSupport {

    private final FilterSpecificationBuilder specificationBuilder;
    private final QueryResultCap queryResultCap;
    private final ReplicationSequences replicationSequences;

    public ResourceDalSupport(FilterSpecificationBuilder specificationBuilder,
                                QueryResultCap queryResultCap,
                                ReplicationSequences replicationSequences) {
        this.specificationBuilder = specificationBuilder;
        this.queryResultCap = queryResultCap;
        this.replicationSequences = replicationSequences;
    }

    public FilterSpecificationBuilder specificationBuilder() {
        return specificationBuilder;
    }

    public QueryResultCap queryResultCap() {
        return queryResultCap;
    }

    public ReplicationSequences replicationSequences() {
        return replicationSequences;
    }
}
