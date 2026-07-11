package com.example.person.domain;

import com.example.infrastructure.persistence.BaseEntity;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate root of the person domain. Mixes simple columns, an embedded value object
 * ({@link Address}), a one-to-many child collection ({@link PhoneNumber}) and an element
 * collection (hobbies).
 */
@Entity
@Table(name = "person")
public class Person extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String firstName;

    @Column(nullable = false, length = 64)
    private String lastName;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    @Column(length = 32)
    private String nickname;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Gender gender;

    /** Annual gross salary. */
    @Column(precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDate hireDate;

    private Integer heightCm;

    private Double weightKg;

    @Embedded
    private Address address;

    // @JsonManagedReference: when the InputMapper populates this entity from an input
    // DTO, Jackson wires each child's back-reference (PhoneNumber.person) to this
    // instance - the declarative equivalent of calling addPhoneNumber() per child.
    @JsonManagedReference
    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PhoneNumber> phoneNumbers = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "person_hobby", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "hobby", length = 64)
    private Set<String> hobbies = new LinkedHashSet<>();

    protected Person() {
        // for JPA
    }

    public Person(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public void addPhoneNumber(PhoneNumber phoneNumber) {
        phoneNumber.setPerson(this);
        phoneNumbers.add(phoneNumber);
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public List<PhoneNumber> getPhoneNumbers() {
        return phoneNumbers;
    }

    public Set<String> getHobbies() {
        return hobbies;
    }

    public void setHobbies(Set<String> hobbies) {
        this.hobbies = hobbies != null ? hobbies : new LinkedHashSet<>();
    }
}
