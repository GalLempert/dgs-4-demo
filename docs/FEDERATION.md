# Cross-service references & federation preparation

Each domain runs as its **own GraphQL service** — its own process, port, database and
schema. `graphql-infrastructure` is the shared framework: the "how" (dispatch,
filtering, validation, replication, errors) is identical everywhere; the "what"
(types, fields, business rules) is unique per service. person-service (:8080) and
company-service (:8081) demonstrate the split.

This document describes how resources in one service reference resources owned by
another **today**, and how that design becomes real Apollo-style federation in a
later PR without reworking anything.

## The reference model (implemented)

A resource may only reference a foreign resource **by key** — the owning service is
the single source of truth for everything else. The infrastructure `reference`
package models this with `ResourceRef`, an id-only view base; a referencing service
adds three small declarations. The living example is `Company.employees`:

1. **Storage** — the company entity stores bare person ids, never person data:

   ```java
   @ElementCollection
   @CollectionTable(name = "company_employee", joinColumns = @JoinColumn(name = "company_id"))
   @Column(name = "person_id")
   private Set<Long> employeeIds;
   ```

2. **View** — a one-line `ResourceRef` subclass per referenced type:

   ```java
   public class PersonRef extends ResourceRef {
       public PersonRef(Long id) { super(id); }
   }
   // in CompanyService.toView:
   view.setEmployees(ResourceRef.toRefs(company.getEmployeeIds(), PersonRef::new));
   ```

3. **Schema** — a stub type named after the REAL type it points at, carrying only
   the key:

   ```graphql
   "FEDERATION-READY STUB - the Person resource is owned by person-service ..."
   type Person {
       id: ID!
   }

   type Company implements Resource {
       ...
       employees: [Person!]!
   }
   ```

A client asking company-service for `companies { employees { id } }` gets the keys
and resolves full persons from person-service (`personById` / `persons` with an `in`
filter). The referencing service never validates the ids — it can't, the data lives
elsewhere; dangling references are the client's (later: the gateway's) concern.

Deliberate properties of this shape:

- **No data duplication** — only keys cross the boundary, so there is nothing to keep
  in sync and the replication feeds of the two services stay independent.
- **The stub does NOT implement `Resource`** — technical fields (version, sequence,
  …) belong to the owning service; a stub that carried them would be a lie.
- **It is exactly the federation entity-reference pattern**, minus the directives.

## What the later federation PR adds (not implemented)

The point of preparing is that this PR only *decorates* — nothing about storage,
views or resolvers changes:

1. **Owning service** (person-service): mark the type as an entity and add the
   entity fetcher DGS uses to resolve references:

   ```graphql
   type Person implements Resource @key(fields: "id") { ... }
   ```

   plus a `@DgsEntityFetcher(name = "Person")` that loads a person by the key —
   a natural infrastructure candidate (`ResourceService.getById` already exists in
   spirit via the DAL).

2. **Referencing service** (company-service): turn the stub into an extension:

   ```graphql
   type Person @key(fields: "id") @extends {
       id: ID! @external
   }
   ```

3. **A federated gateway/router** (Apollo Router or DGS federation gateway) composes
   the subgraphs into one supergraph. Clients then query
   `companies { employees { fullName email } }` against the gateway, which fans out:
   keys from company-service, fields from person-service.

4. Optionally, the reverse edge (`Person.employer`) works the same way in the other
   direction.

## Running both services

```bash
mvn package
java -jar person-service/target/person-service-1.0.0-SNAPSHOT.jar    # :8080
java -jar company-service/target/company-service-1.0.0-SNAPSHOT.jar  # :8081
```

Each serves its own `/graphql`, `/playground` and `/h2-console`. The playgrounds show
only their own service's schema — the "one client for all services" experience is
precisely what the federation gateway will provide.
