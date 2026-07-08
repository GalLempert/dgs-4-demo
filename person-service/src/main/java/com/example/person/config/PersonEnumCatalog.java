package com.example.person.config;

import com.example.infrastructure.enums.EnumCatalog;
import com.example.infrastructure.enums.EnumEntry;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Static enum catalog of the person domain. Swapping this for an external enum
 * service (fetched, cached, hot-reloaded) later means replacing this one bean - the
 * {@code @GraphQLEnum} fields and the schema stay untouched.
 */
@Component
public class PersonEnumCatalog implements EnumCatalog {

    private static final Map<String, Map<String, EnumEntry>> CATALOGS = buildCatalogs();

    @Override
    public Optional<EnumEntry> entry(String catalogName, String code) {
        return Optional.ofNullable(
                CATALOGS.getOrDefault(catalogName, Collections.emptyMap()).get(code));
    }

    private static Map<String, Map<String, EnumEntry>> buildCatalogs() {
        Map<String, Map<String, EnumEntry>> catalogs = new LinkedHashMap<>();
        catalogs.put("gender", genderCatalog());
        catalogs.put("phoneType", phoneTypeCatalog());
        return Collections.unmodifiableMap(catalogs);
    }

    private static Map<String, EnumEntry> genderCatalog() {
        Map<String, EnumEntry> entries = new LinkedHashMap<>();
        entries.put("MALE", new EnumEntry("MALE", "Male", "The person identifies as male"));
        entries.put("FEMALE", new EnumEntry("FEMALE", "Female", "The person identifies as female"));
        entries.put("OTHER", new EnumEntry("OTHER", "Other", "The person identifies as neither male nor female"));
        return entries;
    }

    private static Map<String, EnumEntry> phoneTypeCatalog() {
        Map<String, EnumEntry> entries = new LinkedHashMap<>();
        entries.put("MOBILE", new EnumEntry("MOBILE", "Mobile", "Personal mobile phone"));
        entries.put("HOME", new EnumEntry("HOME", "Home", "Landline at the home address"));
        entries.put("WORK", new EnumEntry("WORK", "Work", "Office phone number"));
        return entries;
    }
}
