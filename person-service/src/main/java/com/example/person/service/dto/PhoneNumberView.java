package com.example.person.service.dto;

import com.example.person.domain.PhoneType;

public class PhoneNumberView {

    private PhoneType type;
    private String number;

    protected PhoneNumberView() {
        // for declarative mapping
    }

    public PhoneNumberView(PhoneType type, String number) {
        this.type = type;
        this.number = number;
    }

    public PhoneType getType() {
        return type;
    }

    public String getNumber() {
        return number;
    }
}
