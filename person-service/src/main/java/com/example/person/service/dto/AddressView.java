package com.example.person.service.dto;

public class AddressView {

    private final String street;
    private final Integer houseNumber;
    private final String city;
    private final String zipCode;
    private final String country;

    public AddressView(String street, Integer houseNumber, String city, String zipCode, String country) {
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
