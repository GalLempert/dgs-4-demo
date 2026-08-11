package com.example.lite.person.domain;

/**
 * The person domain object - a plain POJO standing in for whatever entity class the
 * migrating service already has. GraphQL fields with no dedicated resolver
 * ({@code id}, {@code firstName}...) are read straight off the matching getters;
 * {@code fullName} is computed by a field resolver instead of stored.
 */
public class Person {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String city;

    public Person() {
    }

    public Person(Long id, String firstName, String lastName, String email, String city) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.city = city;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
