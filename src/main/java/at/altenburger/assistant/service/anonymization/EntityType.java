package at.altenburger.assistant.service.anonymization;

/**
 * Types of sensitive entities that can be anonymized.
 * Covers Austrian, German, and English PII categories.
 */
public enum EntityType {
    // Personal Identifiers
    EMAIL("EMAIL"),
    PHONE("PHONE"),
    NAME("NAME"),
    FIRST_NAME("FNAME"),
    LAST_NAME("LNAME"),
    USERNAME("USER"),
    FULL_NAME("FULLNAME"),

    // Financial - General
    CREDIT_CARD("CC"),
    DEBIT_CARD("DEBIT"),
    IBAN("IBAN"),
    BIC_SWIFT("BIC"),
    BANK_ACCOUNT("ACCOUNT"),
    CURRENCY_AMOUNT("AMOUNT"),
    MONETARY_VALUE("MONEY"),

    // Austrian Identifiers
    AUSTRIAN_SVN("AT_SVN"),               // Sozialversicherungsnummer (10 digits)
    AUSTRIAN_STEUERNUMMER("AT_STEUER"),   // Austrian Tax Number
    AUSTRIAN_FIRMENBUCH("AT_FN"),         // Firmenbuchnummer
    AUSTRIAN_ZVR("AT_ZVR"),               // Zentrales Vereinsregister
    AUSTRIAN_UID("AT_UID"),               // Umsatzsteuer-ID (ATU...)
    AUSTRIAN_PASSPORT("AT_PASS"),
    AUSTRIAN_PERSONALAUSWEIS("AT_PERSO"),

    // German Identifiers
    GERMAN_SOZIALVERSICHERUNG("DE_SVN"),
    GERMAN_STEUER_ID("DE_STEUER"),
    GERMAN_PERSONALAUSWEIS("DE_PERSO"),

    // US Identifiers
    SSN("US_SSN"),
    US_PASSPORT("US_PASS"),
    US_DRIVERS_LICENSE("US_DL"),
    US_TIN("US_TIN"),

    // General Government IDs
    PASSPORT("PASSPORT"),
    NATIONAL_ID("NATIONAL_ID"),
    DRIVERS_LICENSE("DL"),

    // Location
    STREET_ADDRESS("STREET"),
    FULL_ADDRESS("ADDRESS"),
    CITY("CITY"),
    POSTAL_CODE("ZIP"),
    COUNTRY("COUNTRY"),
    COORDINATES("COORDS"),
    GPS("GPS"),

    // Vehicle
    LICENSE_PLATE("PLATE"),
    AUSTRIAN_LICENSE_PLATE("AT_PLATE"),
    GERMAN_LICENSE_PLATE("DE_PLATE"),
    VIN("VIN"),

    // Network / Technical
    IP_ADDRESS("IP"),
    IPV6_ADDRESS("IPV6"),
    MAC_ADDRESS("MAC"),
    URL("URL"),
    DOMAIN("DOMAIN"),

    // Health
    HEALTH_INSURANCE_NUMBER("HEALTH_ID"),
    AUSTRIAN_SVNR("AT_SVNR"),             // Austrian health insurance
    GERMAN_KVNR("DE_KVNR"),

    // Date/Time
    DATE_OF_BIRTH("DOB"),
    DATE("DATE"),
    TIME("TIME"),
    DATETIME("DATETIME"),
    AGE("AGE"),

    // Numbers (potentially sensitive)
    QUANTITY("QTY"),
    PERCENTAGE("PERCENT"),
    GENERIC_NUMBER("NUM"),
    LARGE_NUMBER("BIGNUM"),
    DECIMAL_NUMBER("DECIMAL"),
    ORDINAL("ORDINAL"),

    // Communication
    FAX("FAX"),
    MOBILE("MOBILE"),
    LANDLINE("LANDLINE"),

    // Employment
    EMPLOYEE_ID("EMP_ID"),
    COMPANY_NAME("COMPANY"),

    // Other
    UUID("UUID"),
    SERIAL_NUMBER("SERIAL"),
    REFERENCE_NUMBER("REF"),
    ORDER_NUMBER("ORDER"),
    INVOICE_NUMBER("INVOICE"),
    CUSTOMER_NUMBER("CUST_NUM"),
    CONTRACT_NUMBER("CONTRACT"),
    POLICY_NUMBER("POLICY"),

    // Smart Home
    HOME_ROOM("ROOM"),
    HOME_ZONE("ZONE"),
    HOME_SCENE("SCENE"),

    CUSTOM("CUSTOM");

    private final String prefix;

    EntityType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String createPlaceholder(int index) {
        return "[" + prefix + "_" + index + "]";
    }
}