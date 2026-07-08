package com.example.person.bootstrap;

import com.example.person.service.PersonService;
import com.example.person.service.dto.AddressInput;
import com.example.person.service.dto.CreatePersonInput;
import com.example.person.service.dto.PhoneNumberInput;
import com.example.person.domain.Gender;
import com.example.person.domain.PhoneType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Seeds a few demo persons at startup (through the service layer, so the same code path
 * as the createPerson mutation is exercised).
 */
@Component
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final PersonService personService;

    public DemoDataLoader(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public void run(String... args) {
        personService.createPerson(person("Ada", "Lovelace", "ada.lovelace@example.com",
                LocalDate.of(1985, 12, 10), Gender.FEMALE, "540000.00",
                LocalDate.of(2015, 3, 1), 168, 58.0,
                address("Analytical St", 7, "Tel Aviv", "6100000", "Israel"),
                phone(PhoneType.MOBILE, "+972-50-1234567"),
                "chess", "mathematics"));

        personService.createPerson(person("Alan", "Turing", "alan.turing@example.com",
                LocalDate.of(1990, 6, 23), Gender.MALE, "480000.00",
                LocalDate.of(2018, 9, 15), 180, 77.5,
                address("Enigma Ave", 42, "Haifa", "3300000", "Israel"),
                phone(PhoneType.WORK, "+972-4-8765432"),
                "running", "cryptography"));

        personService.createPerson(person("Grace", "Hopper", "grace.hopper@example.com",
                LocalDate.of(1978, 12, 9), Gender.FEMALE, "620000.00",
                LocalDate.of(2010, 1, 20), 165, 62.0,
                address("Compiler Blvd", 1, "Tel Aviv", "6100001", "Israel"),
                phone(PhoneType.HOME, "+972-3-5551234"),
                "sailing", "teaching", "debugging"));

        log.info("Seeded {} demo persons", personService.getAllPersons().size());
    }

    private CreatePersonInput person(String firstName, String lastName, String email,
                                     LocalDate birthDate, Gender gender, String salary,
                                     LocalDate hireDate, int heightCm, double weightKg,
                                     AddressInput address, PhoneNumberInput phone,
                                     String... hobbies) {
        CreatePersonInput input = new CreatePersonInput();
        input.setFirstName(firstName);
        input.setLastName(lastName);
        input.setEmail(email);
        input.setBirthDate(birthDate);
        input.setGender(gender);
        input.setSalary(new BigDecimal(salary));
        input.setHireDate(hireDate);
        input.setHeightCm(heightCm);
        input.setWeightKg(weightKg);
        input.setAddress(address);
        input.setPhoneNumbers(Arrays.asList(phone));
        input.setHobbies(new LinkedHashSet<>(Arrays.asList(hobbies)));
        return input;
    }

    private AddressInput address(String street, int houseNumber, String city, String zip, String country) {
        AddressInput address = new AddressInput();
        address.setStreet(street);
        address.setHouseNumber(houseNumber);
        address.setCity(city);
        address.setZipCode(zip);
        address.setCountry(country);
        return address;
    }

    private PhoneNumberInput phone(PhoneType type, String number) {
        PhoneNumberInput phone = new PhoneNumberInput();
        phone.setType(type);
        phone.setNumber(number);
        return phone;
    }
}
