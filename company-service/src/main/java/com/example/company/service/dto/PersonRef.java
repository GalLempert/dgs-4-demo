package com.example.company.service.dto;

import com.example.infrastructure.reference.ResourceRef;

/**
 * Id-only reference to a Person owned by person-service - backs the federation-ready
 * {@code type Person} stub in this service's schema. Only the key travels; clients
 * (or, in a later PR, the federation gateway) resolve the full Person from
 * person-service.
 */
public class PersonRef extends ResourceRef {

    public PersonRef() {
    }

    public PersonRef(Long id) {
        super(id);
    }
}
