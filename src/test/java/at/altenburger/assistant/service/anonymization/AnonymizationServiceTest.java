package at.altenburger.assistant.service.anonymization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymizationServiceTest {

    private AnonymizationService service;

    @BeforeEach
    void setUp() {
        service = new AnonymizationService();
    }

    @Test
    @DisplayName("Should anonymize Austrian IBAN")
    void shouldAnonymizeAustrianIban() {
        String text = "Überweise auf AT61 1904 3002 3457 3201";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("AT61 1904 3002 3457 3201");
        assertThat(result.getAnonymizedText()).contains("[IBAN_");
        System.out.println("Austrian IBAN: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize Austrian phone number")
    void shouldAnonymizeAustrianPhone() {
        String text = "Ruf mich an unter +43 664 1234567";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("+43 664 1234567");
        System.out.println("Austrian phone: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize Austrian UID")
    void shouldAnonymizeAustrianUid() {
        String text = "Die UID-Nummer ist ATU12345678";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("ATU12345678");
        assertThat(result.getAnonymizedText()).contains("[AT_UID_");
        System.out.println("Austrian UID: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize Austrian Firmenbuchnummer")
    void shouldAnonymizeAustrianFirmenbuch() {
        String text = "Firmenbuchnummer: FN 123456a";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("FN 123456a");
        System.out.println("Austrian Firmenbuch: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize Austrian license plate")
    void shouldAnonymizeAustrianLicensePlate() {
        String text = "Das Auto hat das Kennzeichen W 12345 AB";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("W 12345 AB");
        System.out.println("Austrian license plate: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize Euro currency amounts")
    void shouldAnonymizeEuroCurrency() {
        String text = "Trage 5 EUR Ausgaben für Essen heute ein";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("5 EUR");
        assertThat(result.getAnonymizedText()).contains("[AMOUNT_");
        System.out.println("EUR amount: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize larger Euro amounts")
    void shouldAnonymizeLargerEuroAmounts() {
        String text = "Der Betrag ist €1.234,56 und noch 500 Euro für Miete";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("€1.234,56");
        assertThat(result.getAnonymizedText()).doesNotContain("500 Euro");
        System.out.println("Larger EUR amounts: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize email addresses")
    void shouldAnonymizeEmail() {
        String text = "Kontakt: max.mustermann@beispiel.at";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("max.mustermann@beispiel.at");
        assertThat(result.getAnonymizedText()).contains("[EMAIL_");
        System.out.println("Email: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize Austrian street address")
    void shouldAnonymizeAustrianAddress() {
        String text = "Meine Adresse ist Hauptstraße 15a in Wien";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("Hauptstraße 15a");
        System.out.println("Austrian address: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize German date formats")
    void shouldAnonymizeGermanDates() {
        String text = "Geboren am 15.03.1985 in Salzburg";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("15.03.1985");
        System.out.println("German date: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize credit card number")
    void shouldAnonymizeCreditCard() {
        String text = "Kreditkarte: 4111 1111 1111 1111";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("4111 1111 1111 1111");
        assertThat(result.getAnonymizedText()).contains("[CC_");
        System.out.println("Credit card: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should anonymize IP address")
    void shouldAnonymizeIpAddress() {
        String text = "Server IP: 192.168.1.100";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("192.168.1.100");
        assertThat(result.getAnonymizedText()).contains("[IP_");
        System.out.println("IP address: " + result.getAnonymizedText());
    }

    @Test
    @DisplayName("Should deanonymize correctly")
    void shouldDeanonymizeCorrectly() {
        String original = "Überweise 100 EUR auf AT61 1904 3002 3457 3201";
        AnonymizationResult result = service.anonymize(original);

        String anonymized = result.getAnonymizedText();
        String deanonymized = result.deanonymize(anonymized);

        assertThat(deanonymized).isEqualTo(original);
        System.out.println("Original:     " + original);
        System.out.println("Anonymized:   " + anonymized);
        System.out.println("Deanonymized: " + deanonymized);
    }

    @Test
    @DisplayName("Should handle complex Austrian text with multiple entities")
    void shouldHandleComplexAustrianText() {
        String text = """
            Sehr geehrter Herr Müller,

            Ihre Bestellung Nr. 12345 über €199,99 wurde bearbeitet.
            Bitte überweisen Sie auf IBAN: AT61 1904 3002 3457 3201
            BIC: BKAUATWW

            Lieferadresse: Ringstraße 42, 1010 Wien
            Kontakt: +43 1 23456789
            E-Mail: kunde@firma.at

            Mit freundlichen Grüßen
            """;

        AnonymizationResult result = service.anonymize(text);

        System.out.println("=== Complex Austrian Text Test ===");
        System.out.println("Entities found: " + result.getEntityCount());
        System.out.println("Entity types: " + result.getDetectedEntityTypes());
        System.out.println("\nAnonymized text:");
        System.out.println(result.getAnonymizedText());
        System.out.println("\nMappings:");
        result.getPlaceholderToEntity().forEach((placeholder, entity) ->
            System.out.println("  " + placeholder + " -> " + entity.getOriginalValue() + " (" + entity.getType() + ")"));

        assertThat(result.getEntityCount()).isGreaterThanOrEqualTo(5);
        assertThat(result.getAnonymizedText()).doesNotContain("AT61 1904 3002 3457 3201");
        assertThat(result.getAnonymizedText()).doesNotContain("€199,99");
        assertThat(result.getAnonymizedText()).doesNotContain("+43 1 23456789");
        assertThat(result.getAnonymizedText()).doesNotContain("kunde@firma.at");
    }

    @Test
    @DisplayName("Should handle budget tracking query in German")
    void shouldHandleBudgetTrackingQuery() {
        String text = "Trage 5 EUR Ausgaben für Essen heute ein";
        AnonymizationResult result = service.anonymize(text);

        System.out.println("=== Budget Tracking Query ===");
        System.out.println("Original: " + text);
        System.out.println("Anonymized: " + result.getAnonymizedText());
        System.out.println("Entities: " + result.getDetectedEntityTypes());

        assertThat(result.hasAnonymizedEntities()).isTrue();

        // Test round-trip
        String deanonymized = result.deanonymize(result.getAnonymizedText());
        assertThat(deanonymized).isEqualTo(text);
    }

    @Test
    @DisplayName("Should anonymize smart home room names in German")
    void shouldAnonymizeSmartHomeRooms() {
        String text = "Sind irgendwelche Jalousien im Wohnzimmer offen?";
        AnonymizationResult result = service.anonymize(text);

        System.out.println("=== Smart Home Room Query ===");
        System.out.println("Original: " + text);
        System.out.println("Anonymized: " + result.getAnonymizedText());
        System.out.println("Entities: " + result.getDetectedEntityTypes());

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("Wohnzimmer");
        assertThat(result.getAnonymizedText()).doesNotContain("Jalousien");
        assertThat(result.getAnonymizedText()).contains("[ROOM_");
        assertThat(result.getAnonymizedText()).contains("[DEVICE_");

        // Test round-trip
        String deanonymized = result.deanonymize(result.getAnonymizedText());
        assertThat(deanonymized).isEqualTo(text);
    }

    @Test
    @DisplayName("Should anonymize smart home devices in German")
    void shouldAnonymizeSmartHomeDevices() {
        String text = "Schalte das Licht im Schlafzimmer aus und mach die Heizung an";
        AnonymizationResult result = service.anonymize(text);

        System.out.println("=== Smart Home Device Query ===");
        System.out.println("Original: " + text);
        System.out.println("Anonymized: " + result.getAnonymizedText());
        System.out.println("Entities: " + result.getDetectedEntityTypes());

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("Schlafzimmer");
        assertThat(result.getAnonymizedText()).doesNotContain("Licht");
        assertThat(result.getAnonymizedText()).doesNotContain("Heizung");

        // Test round-trip
        String deanonymized = result.deanonymize(result.getAnonymizedText());
        assertThat(deanonymized).isEqualTo(text);
    }

    @Test
    @DisplayName("Should anonymize smart home in English")
    void shouldAnonymizeSmartHomeEnglish() {
        String text = "Turn on the lights in the living room and close the blinds";
        AnonymizationResult result = service.anonymize(text);

        System.out.println("=== Smart Home English Query ===");
        System.out.println("Original: " + text);
        System.out.println("Anonymized: " + result.getAnonymizedText());
        System.out.println("Entities: " + result.getDetectedEntityTypes());

        assertThat(result.hasAnonymizedEntities()).isTrue();
        assertThat(result.getAnonymizedText()).doesNotContain("living room");
        assertThat(result.getAnonymizedText()).doesNotContain("lights");
        assertThat(result.getAnonymizedText()).doesNotContain("blinds");

        // Test round-trip
        String deanonymized = result.deanonymize(result.getAnonymizedText());
        assertThat(deanonymized).isEqualTo(text);
    }

    @Test
    @DisplayName("Should anonymize complex smart home query")
    void shouldAnonymizeComplexSmartHomeQuery() {
        String text = "Wenn ich das Haus verlasse, schließe alle Jalousien im Wohnzimmer und Schlafzimmer, " +
                     "schalte die Klimaanlage aus und aktiviere die Alarmanlage";
        AnonymizationResult result = service.anonymize(text);

        System.out.println("=== Complex Smart Home Query ===");
        System.out.println("Original: " + text);
        System.out.println("Anonymized: " + result.getAnonymizedText());
        System.out.println("Entities found: " + result.getEntityCount());
        System.out.println("Entity types: " + result.getDetectedEntityTypes());

        assertThat(result.getEntityCount()).isGreaterThanOrEqualTo(4);
        assertThat(result.getAnonymizedText()).doesNotContain("Wohnzimmer");
        assertThat(result.getAnonymizedText()).doesNotContain("Schlafzimmer");
        assertThat(result.getAnonymizedText()).doesNotContain("Jalousien");
        assertThat(result.getAnonymizedText()).doesNotContain("Klimaanlage");
        assertThat(result.getAnonymizedText()).doesNotContain("Alarmanlage");

        // Test round-trip
        String deanonymized = result.deanonymize(result.getAnonymizedText());
        assertThat(deanonymized).isEqualTo(text);
    }
}