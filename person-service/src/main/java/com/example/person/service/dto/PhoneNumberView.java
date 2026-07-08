package com.example.person.service.dto;

import com.example.person.domain.PhoneType;

public class PhoneNumberView {

    private final PhoneType type;
    private final String number;

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
