# Standard mutations — the write side of the framework

Every resource that extends the replicated-resource stack (`BaseEntity` →
`ResourceRepository` → `ResourceDal` → `ResourceService`) inherits **five standard
mutations**, the write counterpart of the four standard queries. Like the queries,
they are manufactured by a factory (`MutationResolverFactory`) and wired with one
`@Bean` per schema field — a domain module writes **no resolver code** for them.

The filtered mutations take the **same filter argument, parsed by the same
`FilterParser`, into the same `FilterCriteria`** as the standard queries: one filter
language across the whole API. See `docs/FILTERING.md`.

## The five mutations

| Mutation | Arguments | Semantics |
|---|---|---|
| save-new (`create<Resource>`) | `input` | Builds a fresh entity from the input and persists it. |
| update (`update<Resources>`) | `filter`, `input` | Fetches every live row matching the filter, **merges** the input's non-null fields onto each, saves the list in one bulk write. Returns the updated views. |
| save-or-update (`saveOrUpdate<Resource>`) | `filter`, `input`, optional `updateInput` | The upsert **orchestrator**: no match → save-new with `input`; matches → update with `updateInput` (or `input` if omitted). It contains no create/update logic of its own — it only decides and delegates. |
| save-or-override (`saveOrOverride<Resource>`) | `input` | Create-or-replace by **natural key**: the service's `naturalKeyOf(input)` hook derives a filter from the input (e.g. person email, company name); a miss creates, a hit **overrides everything** — every field the input declares replaces the row's value, nulls included. Technical fields (id, version, timestamps, sequence) survive. |
| delete (`delete<Resources>`) | `filter` | Soft-deletes every matching row in one bulk write and returns the count. Deletions travel through the replication feed like any other change. |

Guardrails:

- `update`, `saveOrUpdate` and the filtered `delete` **reject an empty filter**
  (`INVALID_ARGUMENT` 400) — an unfiltered write to the whole table is almost
  certainly a client mistake.
- `saveOrOverride` fails loudly (`INTERNAL_ERROR`) when the natural key matches more
  than one row — the domain's `naturalKeyOf` must identify at most one.
- The id-based soft delete (`deleteById` factory method / `softDelete` service
  method) remains available for the classic `delete<Resource>(id)` field.

## The write pipeline

Every mutation runs the same service-layer pipeline (in `ResourceService`):

```
fetch (by filter / natural key, where applicable)
  → apply input to entity        (declaratively, via the object mapper)
  → validate(entity)             (hook: business rules, e.g. unique email)
  → calculateDerivedFields(entity) (hook: stored derived values)
  → save through the DAL         (stamps a fresh replication sequence per row;
                                  bulk writes use ResourceDal.saveAll)
```

"Apply input" comes in three declarative flavors on `DeclarativeMapper`:

- `map(input, class)` — create path: build a new object;
- `merge(input, entity)` — update path: non-null input fields replace values,
  everything else stays (nulls are skipped, so partial payloads work);
- `override(input, entity)` — override path: **all** fields the input type declares
  replace values, nulls included. Fields the input type does not declare (the
  technical fields) are never touched.

`merge`/`override` refill the entity's **existing collection instances** in place
(clear + addAll) instead of assigning new ones — JPA-managed collections
(orphan-removal one-to-many, element collections) must keep their identity or
Hibernate rejects the flush.

## Hooks a domain can override (all optional except the marked one)

| Hook | Default | Override when |
|---|---|---|
| `toEntity(input)` | declarative map to the entity class | construction isn't shape-to-shape |
| `mergeIntoEntity(input, entity)` | declarative merge | post-processing needed (e.g. re-wiring child back-references — see `PersonService.relinkPhoneNumbers`) |
| `overrideEntity(input, entity)` | declarative override | same as merge |
| `validate(entity)` | accept | business rules (throw `ApiException`) — e.g. `PersonService` enforces unique email on creation |
| `calculateDerivedFields(entity)` | no-op | the domain stores derived columns |
| `naturalKeyOf(input)` | throws | **required** when wiring save-or-override; typically one `FilterCriteria.whereEquals(field, value)` |

## Wiring (identical shape to the query side)

Schema (person example — `UpdatePersonInput` is the all-optional partial-update
shape; it can map onto the same Java DTO as creation, since at merge time every
field is optional anyway):

```graphql
updatePersons(filter: PersonFilter!, input: UpdatePersonInput!): [Person!]!
saveOrUpdatePerson(filter: PersonFilter!, input: CreatePersonInput!, updateInput: UpdatePersonInput): [Person!]!
saveOrOverridePerson(input: CreatePersonInput!): Person!
deletePersons(filter: PersonFilter!): Int!
```

Beans (JSON-schema validation is opted into fluently; absent optional arguments
skip validation):

```java
@Bean
public GraphQLResolver updatePersons(MutationResolverFactory factory, PersonService service) {
    return factory.updateByFilter("updatePersons", service, CreatePersonInput.class)
            .validating("input", "person-update");
}
```

company-service is the proof of full inheritance: `CompanyGraphQLConfig` registers
all queries **and** all mutations from the two factories, and `CompanyService`
contributes only the view mapping and `naturalKeyOf` — zero resolver classes, zero
mutation code.
