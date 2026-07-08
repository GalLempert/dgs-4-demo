package com.example.person.service.dto;

import com.example.person.domain.PhoneType;

public class PhoneNumberInput {

    private PhoneType type;
    private String number;

    public PhoneType getType() {
        return type;
    }

    public void setType(PhoneType type) {
        this.type = type;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
