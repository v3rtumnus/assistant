package at.altenburger.assistant.service.anonymization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive anonymization service for detecting and masking PII in text.
 * Supports Austrian, German, and English patterns.
 */
@Slf4j
@Service
public class AnonymizationService {

    private final List<PatternDefinition> patterns;
    private final Map<EntityType, Integer> entityCounters = new EnumMap<>(EntityType.class);

    public AnonymizationService() {
        this.patterns = initializePatterns();
        log.info("AnonymizationService initialized with {} pattern definitions", patterns.size());
    }

    /**
     * Anonymize all detected PII in the given text.
     */
    public AnonymizationResult anonymize(String text) {
        if (text == null || text.isEmpty()) {
            return new AnonymizationResult(text, text, Collections.emptyMap());
        }

        // Reset counters for this anonymization run
        entityCounters.clear();

        // Collect all matches with their positions
        List<MatchResult> allMatches = new ArrayList<>();

        for (PatternDefinition patternDef : patterns) {
            Matcher matcher = patternDef.pattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group();
                // Validate the match if validator exists
                if (patternDef.validator == null || patternDef.validator.isValid(match)) {
                    allMatches.add(new MatchResult(
                            match,
                            patternDef.entityType,
                            matcher.start(),
                            matcher.end(),
                            patternDef.confidence
                    ));
                }
            }
        }

        // Sort by position (descending) to replace from end to start
        allMatches.sort((a, b) -> Integer.compare(b.start, a.start));

        // Remove overlapping matches (keep higher confidence)
        List<MatchResult> filteredMatches = removeOverlaps(allMatches);

        // Sort back to ascending for logging
        filteredMatches.sort(Comparator.comparingInt(m -> m.start));

        // Build anonymized text and mapping
        Map<String, AnonymizationResult.AnonymizedEntity> placeholderMap = new LinkedHashMap<>();
        StringBuilder anonymized = new StringBuilder(text);

        // Process from end to start to maintain positions
        for (int i = filteredMatches.size() - 1; i >= 0; i--) {
            MatchResult match = filteredMatches.get(i);
            String placeholder = getOrCreatePlaceholder(match.value, match.entityType, placeholderMap);

            anonymized.replace(match.start, match.end, placeholder);

            if (!placeholderMap.containsKey(placeholder)) {
                placeholderMap.put(placeholder, new AnonymizationResult.AnonymizedEntity(
                        match.value,
                        match.entityType,
                        match.start,
                        match.end,
                        match.confidence
                ));
            }
        }

        log.debug("Anonymized {} entities in text", placeholderMap.size());

        return new AnonymizationResult(text, anonymized.toString(), placeholderMap);
    }

    /**
     * Get existing placeholder for a value or create a new one.
     */
    private String getOrCreatePlaceholder(String value, EntityType type,
                                           Map<String, AnonymizationResult.AnonymizedEntity> existingMap) {
        // Check if this exact value already has a placeholder
        for (Map.Entry<String, AnonymizationResult.AnonymizedEntity> entry : existingMap.entrySet()) {
            if (entry.getValue().getOriginalValue().equals(value)) {
                return entry.getKey();
            }
        }

        // Create new placeholder
        int index = entityCounters.merge(type, 1, Integer::sum);
        return type.createPlaceholder(index);
    }

    /**
     * Remove overlapping matches, keeping the one with higher confidence.
     */
    private List<MatchResult> removeOverlaps(List<MatchResult> matches) {
        if (matches.size() <= 1) {
            return new ArrayList<>(matches);
        }

        List<MatchResult> result = new ArrayList<>();
        Set<Integer> coveredPositions = new HashSet<>();

        // Sort by confidence (descending) then by length (descending)
        matches.sort((a, b) -> {
            int confCompare = Double.compare(b.confidence, a.confidence);
            if (confCompare != 0) return confCompare;
            return Integer.compare(b.end - b.start, a.end - a.start);
        });

        for (MatchResult match : matches) {
            boolean overlaps = false;
            for (int pos = match.start; pos < match.end; pos++) {
                if (coveredPositions.contains(pos)) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                result.add(match);
                for (int pos = match.start; pos < match.end; pos++) {
                    coveredPositions.add(pos);
                }
            }
        }

        return result;
    }

    /**
     * Initialize all pattern definitions.
     */
    private List<PatternDefinition> initializePatterns() {
        List<PatternDefinition> patterns = new ArrayList<>();

        // ==================== EMAIL ====================
        patterns.add(new PatternDefinition(
                EntityType.EMAIL,
                Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE),
                0.95,
                null
        ));

        // ==================== PHONE NUMBERS ====================
        // Austrian phone numbers
        patterns.add(new PatternDefinition(
                EntityType.PHONE,
                Pattern.compile("(?:\\+43|0043|\\(0\\)|0)\\s*[1-9](?:[\\s./-]?\\d){6,12}"),
                0.90,
                null
        ));

        // German phone numbers
        patterns.add(new PatternDefinition(
                EntityType.PHONE,
                Pattern.compile("(?:\\+49|0049|0)\\s*[1-9](?:[\\s./-]?\\d){6,12}"),
                0.90,
                null
        ));

        // International format
        patterns.add(new PatternDefinition(
                EntityType.PHONE,
                Pattern.compile("\\+[1-9]\\d{0,2}[\\s.-]?(?:\\(\\d{1,4}\\)[\\s.-]?)?(?:\\d[\\s.-]?){6,14}"),
                0.85,
                null
        ));

        // Generic phone with parentheses
        patterns.add(new PatternDefinition(
                EntityType.PHONE,
                Pattern.compile("\\(0\\d{1,5}\\)[\\s./-]?[\\d\\s./-]{6,12}"),
                0.85,
                null
        ));

        // ==================== CREDIT CARDS ====================
        // Visa
        patterns.add(new PatternDefinition(
                EntityType.CREDIT_CARD,
                Pattern.compile("4\\d{3}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}"),
                0.95,
                this::validateLuhn
        ));

        // Mastercard
        patterns.add(new PatternDefinition(
                EntityType.CREDIT_CARD,
                Pattern.compile("(?:5[1-5]\\d{2}|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720)[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}"),
                0.95,
                this::validateLuhn
        ));

        // American Express
        patterns.add(new PatternDefinition(
                EntityType.CREDIT_CARD,
                Pattern.compile("3[47]\\d{2}[\\s-]?\\d{6}[\\s-]?\\d{5}"),
                0.95,
                this::validateLuhn
        ));

        // Diners Club
        patterns.add(new PatternDefinition(
                EntityType.CREDIT_CARD,
                Pattern.compile("3(?:0[0-5]|[68]\\d)\\d[\\s-]?\\d{6}[\\s-]?\\d{4}"),
                0.95,
                this::validateLuhn
        ));

        // Generic 16-digit card number
        patterns.add(new PatternDefinition(
                EntityType.CREDIT_CARD,
                Pattern.compile("\\b\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}\\b"),
                0.80,
                this::validateLuhn
        ));

        // ==================== IBAN ====================
        // Austrian IBAN (AT + 18 digits)
        patterns.add(new PatternDefinition(
                EntityType.IBAN,
                Pattern.compile("AT\\s?\\d{2}\\s?(?:\\d{4}\\s?){4}"),
                0.95,
                this::validateIBAN
        ));

        // German IBAN (DE + 20 digits)
        patterns.add(new PatternDefinition(
                EntityType.IBAN,
                Pattern.compile("DE\\s?\\d{2}\\s?(?:\\d{4}\\s?){4}\\d{2}"),
                0.95,
                this::validateIBAN
        ));

        // Generic European IBAN
        patterns.add(new PatternDefinition(
                EntityType.IBAN,
                Pattern.compile("[A-Z]{2}\\s?\\d{2}\\s?(?:[A-Z0-9]{4}\\s?){2,7}[A-Z0-9]{1,4}"),
                0.90,
                this::validateIBAN
        ));

        // ==================== BIC/SWIFT ====================
        patterns.add(new PatternDefinition(
                EntityType.BIC_SWIFT,
                Pattern.compile("\\b[A-Z]{4}AT[A-Z0-9]{2}(?:[A-Z0-9]{3})?\\b"),  // Austrian BIC
                0.90,
                null
        ));
        patterns.add(new PatternDefinition(
                EntityType.BIC_SWIFT,
                Pattern.compile("\\b[A-Z]{4}DE[A-Z0-9]{2}(?:[A-Z0-9]{3})?\\b"),  // German BIC
                0.90,
                null
        ));
        patterns.add(new PatternDefinition(
                EntityType.BIC_SWIFT,
                Pattern.compile("\\b[A-Z]{6}[A-Z0-9]{2}(?:[A-Z0-9]{3})?\\b"),    // Generic BIC
                0.80,
                null
        ));

        // ==================== AUSTRIAN IDENTIFIERS ====================
        // Austrian Sozialversicherungsnummer (10 digits: NNNN DDMMYY)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_SVN,
                Pattern.compile("\\b\\d{4}[\\s-]?(?:0[1-9]|[12]\\d|3[01])(?:0[1-9]|1[0-2])\\d{2}\\b"),
                0.85,
                null
        ));

        // Austrian UID (ATU + 8 digits)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_UID,
                Pattern.compile("ATU\\s?\\d{8}\\b", Pattern.CASE_INSENSITIVE),
                0.95,
                null
        ));

        // Austrian Firmenbuchnummer (FN + number + letter)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_FIRMENBUCH,
                Pattern.compile("FN\\s?\\d{5,6}\\s?[a-zA-Z]\\b", Pattern.CASE_INSENSITIVE),
                0.95,
                null
        ));

        // Austrian ZVR-Zahl (9 digits)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_ZVR,
                Pattern.compile("ZVR[:\\s-]?\\d{9}\\b", Pattern.CASE_INSENSITIVE),
                0.95,
                null
        ));

        // Austrian Steuernummer (2-3 digits / 3 digits / 4-5 digits)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_STEUERNUMMER,
                Pattern.compile("\\b\\d{2,3}[/\\s-]\\d{3}[/\\s-]\\d{4,5}\\b"),
                0.80,
                null
        ));

        // ==================== GERMAN IDENTIFIERS ====================
        // German Steuer-ID (11 digits)
        patterns.add(new PatternDefinition(
                EntityType.GERMAN_STEUER_ID,
                Pattern.compile("\\b\\d{2}\\s?\\d{3}\\s?\\d{3}\\s?\\d{3}\\b"),
                0.75,
                null
        ));

        // German Sozialversicherungsnummer (12 characters: area code + DOB + serial + check)
        patterns.add(new PatternDefinition(
                EntityType.GERMAN_SOZIALVERSICHERUNG,
                Pattern.compile("\\b\\d{2}[\\s]?(?:0[1-9]|[12]\\d|3[01])(?:0[1-9]|1[0-2])\\d{2}[\\s]?[A-Z]\\d{3}[\\s]?\\d\\b"),
                0.85,
                null
        ));

        // ==================== US IDENTIFIERS ====================
        // US Social Security Number
        patterns.add(new PatternDefinition(
                EntityType.SSN,
                Pattern.compile("\\b\\d{3}[\\s-]\\d{2}[\\s-]\\d{4}\\b"),
                0.90,
                null
        ));

        // US TIN/EIN
        patterns.add(new PatternDefinition(
                EntityType.US_TIN,
                Pattern.compile("\\b\\d{2}[\\s-]\\d{7}\\b"),
                0.80,
                null
        ));

        // ==================== PASSPORT ====================
        // Austrian passport (letter + 7 digits)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_PASSPORT,
                Pattern.compile("\\b[A-Z]\\d{7}\\b"),
                0.70,
                null
        ));

        // Generic passport number
        patterns.add(new PatternDefinition(
                EntityType.PASSPORT,
                Pattern.compile("(?i)(?:pass(?:port)?|reisepass)[:\\s#-]*([A-Z0-9]{6,12})"),
                0.85,
                null
        ));

        // ==================== LICENSE PLATES ====================
        // Austrian license plate (W 12345 A, G 1234 AB, etc.)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_LICENSE_PLATE,
                Pattern.compile("\\b(?:W|G|L|S|K|ST|OÖ|NÖ|T|V|B|NO|WU|WB|MD|GF|HL|KR|WT|BN|KS|BL|EU|GD|GS|HB|HF|HO|IL|JE|JO|JU|KB|KI|KL|KO|LA|LB|LE|LF|LI|LL|LN|LZ|MA|ME|MI|MU|MZ|ND|NK|OP|OW|PE|PL|RA|RE|RI|RO|SB|SD|SE|SK|SL|SP|SR|SW|SZ|TA|TU|UU|VB|VK|VL|VO|WE|WL|WN|WO|WR|WT|WU|WY|ZE|ZT)\\s?\\d{1,5}\\s?[A-Z]{1,3}\\b"),
                0.90,
                null
        ));

        // German license plate
        patterns.add(new PatternDefinition(
                EntityType.GERMAN_LICENSE_PLATE,
                Pattern.compile("\\b[A-ZÄÖÜ]{1,3}[\\s-]?[A-Z]{1,2}[\\s-]?\\d{1,4}[EH]?\\b"),
                0.85,
                null
        ));

        // ==================== VIN ====================
        patterns.add(new PatternDefinition(
                EntityType.VIN,
                Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b"),
                0.80,
                this::validateVIN
        ));

        // ==================== IP ADDRESSES ====================
        // IPv4
        patterns.add(new PatternDefinition(
                EntityType.IP_ADDRESS,
                Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"),
                0.95,
                null
        ));

        // IPv6
        patterns.add(new PatternDefinition(
                EntityType.IPV6_ADDRESS,
                Pattern.compile("(?i)\\b(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\\b|\\b(?:[0-9a-f]{1,4}:){1,7}:|\\b(?:[0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}\\b"),
                0.95,
                null
        ));

        // ==================== MAC ADDRESS ====================
        patterns.add(new PatternDefinition(
                EntityType.MAC_ADDRESS,
                Pattern.compile("(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b"),
                0.95,
                null
        ));

        // ==================== URLs ====================
        patterns.add(new PatternDefinition(
                EntityType.URL,
                Pattern.compile("https?://[^\\s<>\"{}|\\\\^`\\[\\]]+"),
                0.95,
                null
        ));

        // ==================== COORDINATES ====================
        // GPS coordinates
        patterns.add(new PatternDefinition(
                EntityType.COORDINATES,
                Pattern.compile("[-+]?(?:[1-8]?\\d(?:\\.\\d+)?|90(?:\\.0+)?)[,\\s]+[-+]?(?:180(?:\\.0+)?|(?:1[0-7]\\d|[1-9]?\\d)(?:\\.\\d+)?)"),
                0.85,
                null
        ));

        // ==================== DATES ====================
        // European date format (DD.MM.YYYY, DD/MM/YYYY, DD-MM-YYYY)
        patterns.add(new PatternDefinition(
                EntityType.DATE,
                Pattern.compile("\\b(?:0?[1-9]|[12]\\d|3[01])[./-](?:0?[1-9]|1[0-2])[./-](?:19|20)?\\d{2}\\b"),
                0.80,
                null
        ));

        // US date format (MM/DD/YYYY)
        patterns.add(new PatternDefinition(
                EntityType.DATE,
                Pattern.compile("\\b(?:0?[1-9]|1[0-2])[/](?:0?[1-9]|[12]\\d|3[01])[/](?:19|20)\\d{2}\\b"),
                0.75,
                null
        ));

        // ISO date format (YYYY-MM-DD)
        patterns.add(new PatternDefinition(
                EntityType.DATE,
                Pattern.compile("\\b(?:19|20)\\d{2}[-](?:0[1-9]|1[0-2])[-](?:0[1-9]|[12]\\d|3[01])\\b"),
                0.85,
                null
        ));

        // Written date format (German)
        patterns.add(new PatternDefinition(
                EntityType.DATE,
                Pattern.compile("\\b(?:0?[1-9]|[12]\\d|3[01])\\.?\\s*(?:Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Jänner)\\s*(?:19|20)?\\d{2}\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // Written date format (English)
        patterns.add(new PatternDefinition(
                EntityType.DATE,
                Pattern.compile("\\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+(?:0?[1-9]|[12]\\d|3[01])(?:st|nd|rd|th)?,?\\s*(?:19|20)?\\d{2}\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // ==================== CURRENCY AMOUNTS ====================
        // Euro amounts (€, EUR)
        patterns.add(new PatternDefinition(
                EntityType.CURRENCY_AMOUNT,
                Pattern.compile("(?:€\\s?|EUR\\s?)\\d{1,3}(?:[.,']?\\d{3})*(?:[.,]\\d{1,2})?|\\d{1,3}(?:[.,']?\\d{3})*(?:[.,]\\d{1,2})?\\s?(?:€|EUR|Euro|Euros)\\b", Pattern.CASE_INSENSITIVE),
                0.90,
                null
        ));

        // Dollar amounts ($, USD)
        patterns.add(new PatternDefinition(
                EntityType.CURRENCY_AMOUNT,
                Pattern.compile("(?:\\$\\s?|USD\\s?)\\d{1,3}(?:[,']?\\d{3})*(?:\\.\\d{1,2})?|\\d{1,3}(?:[,']?\\d{3})*(?:\\.\\d{1,2})?\\s?(?:\\$|USD|Dollar|Dollars)\\b", Pattern.CASE_INSENSITIVE),
                0.90,
                null
        ));

        // Swiss Franc (CHF)
        patterns.add(new PatternDefinition(
                EntityType.CURRENCY_AMOUNT,
                Pattern.compile("(?:CHF\\s?)\\d{1,3}(?:[.,']?\\d{3})*(?:[.,]\\d{1,2})?|\\d{1,3}(?:[.,']?\\d{3})*(?:[.,]\\d{1,2})?\\s?(?:CHF|Franken|Fr\\.)\\b", Pattern.CASE_INSENSITIVE),
                0.90,
                null
        ));

        // British Pound (£, GBP)
        patterns.add(new PatternDefinition(
                EntityType.CURRENCY_AMOUNT,
                Pattern.compile("(?:£\\s?|GBP\\s?)\\d{1,3}(?:[,']?\\d{3})*(?:\\.\\d{1,2})?|\\d{1,3}(?:[,']?\\d{3})*(?:\\.\\d{1,2})?\\s?(?:£|GBP|Pound|Pounds)\\b", Pattern.CASE_INSENSITIVE),
                0.90,
                null
        ));

        // Generic currency with number
        patterns.add(new PatternDefinition(
                EntityType.MONETARY_VALUE,
                Pattern.compile("\\b\\d{1,3}(?:[.,']?\\d{3})*(?:[.,]\\d{1,2})?\\s?(?:EUR|USD|GBP|CHF|AUD|CAD|JPY|CNY|INR|RUB|BRL|KRW|SEK|NOK|DKK|PLN|CZK|HUF|RON|BGN|HRK|TRY)\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // ==================== NUMBERS WITH CONTEXT ====================
        // Quantity with unit (German/English)
        patterns.add(new PatternDefinition(
                EntityType.QUANTITY,
                Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\s*(?:kg|g|mg|l|ml|cl|dl|km|m|cm|mm|ha|qm|m²|m³|Stück|Stk|pcs|pieces|units?)\\b", Pattern.CASE_INSENSITIVE),
                0.75,
                null
        ));

        // Percentage
        patterns.add(new PatternDefinition(
                EntityType.PERCENTAGE,
                Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\s?(?:%|Prozent|percent)\\b", Pattern.CASE_INSENSITIVE),
                0.80,
                null
        ));

        // Age
        patterns.add(new PatternDefinition(
                EntityType.AGE,
                Pattern.compile("\\b\\d{1,3}\\s*(?:Jahre?|years?|J\\.|y\\.o\\.|yo)\\s*(?:alt)?\\b", Pattern.CASE_INSENSITIVE),
                0.80,
                null
        ));

        // ==================== POSTAL CODES ====================
        // Austrian postal code (4 digits)
        patterns.add(new PatternDefinition(
                EntityType.POSTAL_CODE,
                Pattern.compile("\\b[1-9]\\d{3}\\b(?=\\s+(?:[A-ZÄÖÜ][a-zäöüß]+|Wien|Graz|Linz|Salzburg|Innsbruck))"),
                0.80,
                null
        ));

        // German postal code (5 digits)
        patterns.add(new PatternDefinition(
                EntityType.POSTAL_CODE,
                Pattern.compile("\\b[0-9]{5}\\b(?=\\s+(?:[A-ZÄÖÜ][a-zäöüß]+|Berlin|Hamburg|München|Köln|Frankfurt))"),
                0.80,
                null
        ));

        // US ZIP code
        patterns.add(new PatternDefinition(
                EntityType.POSTAL_CODE,
                Pattern.compile("\\b\\d{5}(?:-\\d{4})?\\b"),
                0.70,
                null
        ));

        // ==================== ADDRESSES ====================
        // German/Austrian street address
        patterns.add(new PatternDefinition(
                EntityType.STREET_ADDRESS,
                Pattern.compile("(?:[A-ZÄÖÜ][a-zäöüß]+(?:straße|strasse|gasse|weg|platz|ring|allee|damm|ufer|park|hof|berg))[\\s,]+\\d{1,4}\\s?[a-zA-Z]?(?:[/-]\\d{1,4})?", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // English street address
        patterns.add(new PatternDefinition(
                EntityType.STREET_ADDRESS,
                Pattern.compile("\\d{1,5}\\s+(?:[A-Z][a-z]+\\s+){1,3}(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Way|Place|Pl)\\.?(?:\\s+(?:Apt|Suite|Unit|#)\\s*\\d+)?", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // ==================== CUSTOMER/ORDER NUMBERS ====================
        // Generic reference numbers with prefixes
        patterns.add(new PatternDefinition(
                EntityType.REFERENCE_NUMBER,
                Pattern.compile("(?i)(?:ref|reference|bestellung|order|auftrag|rechnung|invoice|kunden?|customer|vertrag|contract|police|policy)[.:\\s#-]*([A-Z0-9]{4,20})"),
                0.80,
                null
        ));

        // Order/Invoice number patterns
        patterns.add(new PatternDefinition(
                EntityType.ORDER_NUMBER,
                Pattern.compile("(?i)(?:order|bestellung|auftrag)[\\s#-]*(?:nr\\.?|no\\.?|nummer)?[:\\s]*([A-Z0-9-]{4,15})"),
                0.85,
                null
        ));

        patterns.add(new PatternDefinition(
                EntityType.INVOICE_NUMBER,
                Pattern.compile("(?i)(?:invoice|rechnung|faktura)[\\s#-]*(?:nr\\.?|no\\.?|nummer)?[:\\s]*([A-Z0-9-]{4,15})"),
                0.85,
                null
        ));

        patterns.add(new PatternDefinition(
                EntityType.CUSTOMER_NUMBER,
                Pattern.compile("(?i)(?:customer|kunden?)[\\s#-]*(?:nr\\.?|no\\.?|nummer|id)?[:\\s]*([A-Z0-9-]{4,15})"),
                0.85,
                null
        ));

        // ==================== UUID ====================
        patterns.add(new PatternDefinition(
                EntityType.UUID,
                Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE),
                0.95,
                null
        ));

        // ==================== SMART HOME - ROOMS ====================
        // German room names
        patterns.add(new PatternDefinition(
                EntityType.HOME_ROOM,
                Pattern.compile("\\b(?:Wohnzimmer|Schlafzimmer|Kinderzimmer|Badezimmer|Küche|Kueche|Esszimmer|Arbeitszimmer|Büro|Buero|Gästezimmer|Gaestezimmer|Flur|Diele|Eingang|Eingangshalle|Vorraum|Vorzimmer|Abstellraum|Abstellkammer|Keller|Dachboden|Speicher|Garage|Carport|Terrasse|Balkon|Garten|Wintergarten|Hauswirtschaftsraum|Waschküche|Waschkueche|WC|Toilette|Gäste-WC|Gaeste-WC|Ankleidezimmer|Ankleide|Hobbyraum|Spielzimmer|Heimkino|Sauna|Fitnessraum|Wellnessbereich|Hauswirtschaft|Speis|Speisekammer)\\b", Pattern.CASE_INSENSITIVE),
                0.90,
                null
        ));

        // English room names
        patterns.add(new PatternDefinition(
                EntityType.HOME_ROOM,
                Pattern.compile("\\b(?:living room|living-room|livingroom|bedroom|bathroom|kitchen|dining room|dining-room|diningroom|study|office|home office|guest room|guest-room|guestroom|hallway|hall|entryway|entrance|foyer|storage room|storage|basement|cellar|attic|loft|garage|carport|terrace|patio|balcony|garden|yard|backyard|front yard|laundry room|laundry|utility room|restroom|toilet|powder room|closet|walk-in closet|playroom|game room|media room|home theater|home theatre|sauna|gym|fitness room|spa|pantry|mudroom|nursery|den|sunroom|conservatory|master bedroom|master bath)\\b", Pattern.CASE_INSENSITIVE),
                0.90,
                null
        ));

        // German compound room names with floor/level
        patterns.add(new PatternDefinition(
                EntityType.HOME_ROOM,
                Pattern.compile("\\b(?:(?:Erd|Ober|Unter|Dach)geschoss(?:[-\\s]?(?:Wohnzimmer|Schlafzimmer|Bad|Küche|Flur))?|(?:1\\.|2\\.|3\\.|erstes?|zweites?|drittes?)\\s*(?:Stock(?:werk)?|OG|Etage)(?:[-\\s]?(?:Wohnzimmer|Schlafzimmer|Bad|Küche|Flur))?)\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // Room with direction/position (German)
        patterns.add(new PatternDefinition(
                EntityType.HOME_ROOM,
                Pattern.compile("\\b(?:(?:vorderes?|hinteres?|linkes?|rechtes?|oberes?|unteres?|großes?|kleines?)\\s+(?:Zimmer|Schlafzimmer|Bad|Badezimmer))\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // ==================== SMART HOME - ZONES ====================
        // German zone names
        patterns.add(new PatternDefinition(
                EntityType.HOME_ZONE,
                Pattern.compile("\\b(?:Erdgeschoss|EG|Obergeschoss|OG|Untergeschoss|UG|Dachgeschoss|DG|Keller|Parterre|(?:1\\.|2\\.|3\\.)\\s*(?:Stock|Etage|OG)|Außenbereich|Aussenbereich|Innenbereich|Wohnbereich|Schlafbereich|Eingangsbereich|Technikraum)\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // English zone names
        patterns.add(new PatternDefinition(
                EntityType.HOME_ZONE,
                Pattern.compile("\\b(?:ground floor|first floor|second floor|third floor|basement|attic|upstairs|downstairs|outdoor area|indoor area|living area|sleeping area|entrance area|utility area)\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // ==================== SMART HOME - SCENES ====================
        // German scene names - explicit scene references with context
        patterns.add(new PatternDefinition(
                EntityType.HOME_SCENE,
                Pattern.compile("(?i)(?:Szene[n]?|Modus)[:\\s]+\\w+"),
                0.90,
                null
        ));

        // German scene names - compound terms that are clearly scenes
        patterns.add(new PatternDefinition(
                EntityType.HOME_SCENE,
                Pattern.compile("\\b(?:Guten\\s*Morgen|Gute\\s*Nacht|Aufwachen|Schlafengehen|Abwesenheitsmodus|Anwesenheitsmodus|Urlaubsmodus|Filmabend|Kinoabend|Partymodus|Entspannungsmodus|Lesemodus|Musik\\s*hören)\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // English scene names - explicit scene references with context
        patterns.add(new PatternDefinition(
                EntityType.HOME_SCENE,
                Pattern.compile("(?i)(?:scene|mode)[:\\s]+\\w+"),
                0.90,
                null
        ));

        // English scene names - compound terms that are clearly scenes
        patterns.add(new PatternDefinition(
                EntityType.HOME_SCENE,
                Pattern.compile("\\b(?:good\\s*morning|good\\s*night|wake\\s*up|bedtime|away\\s*mode|vacation\\s*mode|movie\\s*night|party\\s*mode|night\\s*mode|day\\s*mode|eco\\s*mode|sleep\\s*mode)\\b", Pattern.CASE_INSENSITIVE),
                0.85,
                null
        ));

        // ==================== HEALTH INSURANCE ====================
        // Austrian e-card number (10 digits starting with SVN)
        patterns.add(new PatternDefinition(
                EntityType.AUSTRIAN_SVNR,
                Pattern.compile("(?i)(?:e-?card|svnr?|sozialversicherung)[:\\s]*\\d{10}"),
                0.90,
                null
        ));

        // German Krankenversichertennummer
        patterns.add(new PatternDefinition(
                EntityType.GERMAN_KVNR,
                Pattern.compile("\\b[A-Z]\\d{9}\\b"),
                0.75,
                null
        ));

        // ==================== GENERIC NUMBERS (Lower priority) ====================
        // Large numbers (potential account numbers, IDs, etc.)
        patterns.add(new PatternDefinition(
                EntityType.LARGE_NUMBER,
                Pattern.compile("\\b\\d{8,16}\\b"),
                0.50,
                null
        ));

        // Decimal numbers with context
        patterns.add(new PatternDefinition(
                EntityType.DECIMAL_NUMBER,
                Pattern.compile("\\b\\d{1,6}[.,]\\d{1,4}\\b"),
                0.40,
                null
        ));

        return patterns;
    }

    // ==================== VALIDATORS ====================

    private boolean validateLuhn(String number) {
        String digits = number.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d+") || digits.length() < 13) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(digits.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private boolean validateIBAN(String iban) {
        String normalized = iban.replaceAll("\\s", "").toUpperCase();
        if (normalized.length() < 15 || normalized.length() > 34) {
            return false;
        }

        // Move first 4 chars to end
        String rearranged = normalized.substring(4) + normalized.substring(0, 4);

        // Convert letters to numbers (A=10, B=11, etc.)
        StringBuilder numericBuilder = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numericBuilder.append(c - 'A' + 10);
            } else {
                numericBuilder.append(c);
            }
        }

        // Modulo 97
        try {
            java.math.BigInteger bigInt = new java.math.BigInteger(numericBuilder.toString());
            return bigInt.mod(java.math.BigInteger.valueOf(97)).intValue() == 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validateVIN(String vin) {
        if (vin.length() != 17) return false;

        // VIN cannot contain I, O, Q
        if (vin.matches(".*[IOQ].*")) return false;

        // Simple check - real VIN validation is more complex
        return vin.matches("[A-HJ-NPR-Z0-9]{17}");
    }

    // ==================== INTERNAL CLASSES ====================

    private static class PatternDefinition {
        final EntityType entityType;
        final Pattern pattern;
        final double confidence;
        final Validator validator;

        PatternDefinition(EntityType entityType, Pattern pattern, double confidence, Validator validator) {
            this.entityType = entityType;
            this.pattern = pattern;
            this.confidence = confidence;
            this.validator = validator;
        }
    }

    @FunctionalInterface
    private interface Validator {
        boolean isValid(String value);
    }

    private static class MatchResult {
        final String value;
        final EntityType entityType;
        final int start;
        final int end;
        final double confidence;

        MatchResult(String value, EntityType entityType, int start, int end, double confidence) {
            this.value = value;
            this.entityType = entityType;
            this.start = start;
            this.end = end;
            this.confidence = confidence;
        }
    }
}