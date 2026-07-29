package com.example.infrastructure.reference;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An id-only reference to a resource owned by ANOTHER service - the federation-ready
 * way to link resources across service boundaries (see the package javadoc). A domain
 * declares one subclass per referenced type and a matching stub type in its schema:
 *
 * <pre>{@code
 * // Java - in the referencing service
 * public class PersonRef extends ResourceRef {
 *     public PersonRef(Long id) { super(id); }
 * }
 *
 * # schema - the stub carries only the key; the owning service has the real type
 * type Person { id: ID! }
 * type Company { ... employees: [Person!]! }
 * }</pre>
 *
 * <p>Only the key travels: the referencing service stores foreign ids and never
 * duplicates (or validates) the referenced resource's data - the owning service is
 * the single source of truth, and a client that needs more than the id queries the
 * owning service (until the federation gateway of a later PR does it transparently).
 */
public abstract class ResourceRef {

    private Long id;

    protected ResourceRef() {
        // for mapping frameworks
    }

    protected ResourceRef(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** Convenience for mapping a stored id collection to reference views, in order. */
    public static <R extends ResourceRef> List<R> toRefs(Collection<Long> ids, Function<Long, R> factory) {
        return ids.stream().map(factory).collect(Collectors.toList());
    }
}
