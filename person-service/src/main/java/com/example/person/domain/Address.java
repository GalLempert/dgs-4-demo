package com.example.person.domain;

import javax.persistence.Column;
import javax.persistence.Embeddable;

/**
 * Nested value object embedded into the person table (address_* columns).
 */
@Embeddable
public class Address {

    @Column(name = "address_street")
    private String street;

    @Column(name = "address_house_number")
    private Integer houseNumber;

    @Column(name = "address_city")
    private String city;

    @Column(name = "address_zip_code")
    private String zipCode;

    @Column(name = "address_country")
    private String country;

    protected Address() {
        // for JPA
    }

    public Address(String street, Integer houseNumber, String city, String zipCode, String country) {
        this.street = street;
        this.houseNumber = houseNumber;
        this.city = city;
        this.zipCode = zipCode;
        this.country = country;
    }

    public String getStreet() {
        return street;
    }

    public Integer getHouseNumber() {
        return houseNumber;
    }

    public String getCity() {
        return city;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getCountry() {
        return country;
    }
}
