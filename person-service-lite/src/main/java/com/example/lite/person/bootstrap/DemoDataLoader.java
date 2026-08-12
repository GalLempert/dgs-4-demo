package com.example.lite.person.bootstrap;

import com.example.lite.person.service.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Seeds a few demo persons at startup, through the service layer. */
@Component
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final PersonService personService;

    public DemoDataLoader(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public void run(String... args) {
        personService.createPerson("Ada", "Lovelace", "ada.lovelace@example.com", "Tel Aviv");
        personService.createPerson("Alan", "Turing", "alan.turing@example.com", "Haifa");
        personService.createPerson("Grace", "Hopper", "grace.hopper@example.com", "Tel Aviv");
        log.info("Seeded {} demo persons", personService.countPersons());
    }
}
