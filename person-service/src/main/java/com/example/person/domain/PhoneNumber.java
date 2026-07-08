package com.example.person.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Nested child entity: a person owns a list of phone numbers (one-to-many).
 */
@Entity
@Table(name = "phone_number")
public class PhoneNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PhoneType type;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    protected PhoneNumber() {
        // for JPA
    }

    public PhoneNumber(PhoneType type, String number) {
        this.type = type;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public PhoneType getType() {
        return type;
    }

    public String getNumber() {
        return number;
    }

    public Person getPerson() {
        return person;
    }

    void setPerson(Person person) {
        this.person = person;
    }
}
