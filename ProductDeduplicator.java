import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class ProductDeduplicator {
    private static final Pattern NON_LETTER_OR_NUMBER = Pattern.compile("[^\\p{L}\\p{N}\\s]");// a regex pattern to match any character that is not a letter, number, or whitespace.
    private static final Pattern SAMSUNG_MODEL_PATTERN = Pattern.compile("s(\\d+)"); // a regex pattern to match Samsung model tokens that start with "s" followed by digits, capturing the digits as a group for normalization (e.g., "S23" would capture "23").
    private static final Map<String, String> TOKEN_CANONICAL_MAP = createCanonicalMap();
    private static final Set<String> BRAND_TOKENS = new HashSet<>(Arrays.asList("samsung", "iphone", "google", "ipad"));// the set of known brand tokens used to identify the brand in the product name. This helps to build a more deterministic key.
    private static final Set<String> FAMILY_TOKENS = new HashSet<>(Arrays.asList("galaxy", "pixel", "air", "pro"));// the set of known family tokens used to identify the product family in the product name. This helps to build a more deterministic key.
    private static final Set<String> HEBREW_TOKENS = createScriptVocabulary(true);
    private static final Set<String> ENGLISH_TOKENS = createScriptVocabulary(false);
    public static void main(String[] args) throws IOException {
        List<Product> products = new ArrayList<>();
        //test duplicates with unwanted characters and different spacing both in hebrew and english, should return the price 749.99 for both products.
        products.add(new Product("Samsung Galaxy S25", 799.99));
        products.add(new Product("SAMSUNG   GALAXY S25!!!", 749.99));
        products.add(new Product("Google Pixel 9", 799.00));
        products.add(new Product("גוגל פיקסל      9", 749.00));

        //test duplicates with different casing, should return the price 750.00 for this product.
        products.add(new Product("Google PixeL 8", 799.00));
        products.add(new Product("google pixel 8", 750.00));

        // test duplicates with Hebrew and English variants, should return the price 650.00 for this product.
        products.add(new Product("סמסונג גלקסי S24", 710.00));
        products.add(new Product("Samsung glaxy S24", 650.00));
        // test duplicates with Hebrew and English variants when part is not mentioned "galaxy" missing, should return the price 450.00 for this product.
        products.add(new Product("Samsung S23", 500.00));
        products.add(new Product("סמסונג גלקסי 23", 450.00));//should return the price 450.00 for this product.

       //test duplicates with Hebrew and English variants with a likely typo in Hebrew, should return the price 699.00 for this product.
        products.add(new Product("IPhone 15 Pro", 99.99));
        products.add(new Product("אייפן 15 פרו", 9.99));

        //test duplicates with English variants with a likely typo  should return the price 599.00 for this product.
        products.add(new Product("iPad Air", 620.00));
        products.add(new Product("iPd Air", 599.00));

        //test Hebrew is first and English is second, output name should be the English name and lowest price 680.00.
        products.add(new Product("סמסונג גלקסי S26", 700.00));
        products.add(new Product("Samsung Galaxy S26", 680.00));

        //test Hebrew-only duplicates with typo, should merge and return the lowest price 430.00 there is no English variant so the name is kept in Hebrew.
        products.add(new Product("סמסונג גלקסי 22", 470.00));
        products.add(new Product("סמסונ גלאקסי 22", 430.00));

        //test close model numbers are not duplicates, S23 and S24 should stay separate.
        products.add(new Product("Samsung Galaxy S23", 510.00));
        products.add(new Product("Samsung Galaxy S24", 520.00));

        //test empty and punctuation-only names are ignored by normalization and should not create output products.
        products.add(new Product("   ", 1.00));
        products.add(new Product("!!!", 2.00));

        //test laptop duplicates with case/spacing/punctuation differences, should return the price 1099.00.
        products.add(new Product("Dell XPS 13", 1199.00));
        products.add(new Product("DELL   XPS 13!!!", 1099.00));

        //test TV duplicates with case differences, should return the price 840.00.
        products.add(new Product("Sony Bravia 55", 900.00));
        products.add(new Product("SONY BRAVIA 55", 840.00));

        //test different TV models are not duplicates, 55 and 65 should stay separate.
        products.add(new Product("Sony Bravia 65", 1200.00));

        //test appliance duplicates with word-order and punctuation differences, should return the price 680.00.
        products.add(new Product("Bosch Series 6 Washer", 700.00));
        products.add(new Product("Bosch Washer Series 6!!!", 680.00));

        //test shoe duplicates with word-order differences, should return the price 130.00.
        products.add(new Product("Nike Air Max 90", 150.00));
        products.add(new Product("Max Nike Air 90", 130.00));

        List<Product> deduplicated = deduplicateProducts(products);
        System.out.println("Input products: " + products.size());
        System.out.println("Unique products: " + deduplicated.size());
        System.out.println("Merged duplicates: " + (products.size() - deduplicated.size()));
            for (Product product : deduplicated) {
                System.out.println(product);
            }
    }
    /** Groups products by their normalized names. Each product is normalized using the normalize method, and products with the same normalized name are grouped together in a map. The key of the map is the normalized name, and the value is a list of products that share that normalized name. This method helps to identify groups of duplicate products based on their normalized representations, which can then be merged to retain only unique products with their lowest prices.
     * @param products the list of products to group
     * @return a map where each key is a normalized product name and the corresponding value is a list of products that share that normalized name
     */
    public static Map<String, List<Product>> groupDuplicates(List<Product> products) {
        Map<String, List<Product>> groups = new LinkedHashMap<>();
        for (Product product : products) {
            String key = normalize(product.getName());
            if (key.isEmpty()) {
                continue;
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(product);// used to add new product if not already exist.
        }
        return groups;
    }
    /** 
      * Merges a group of products into a single product with the lowest price. The merged product retains the name of the first product in the group, but its price is set to the lowest price found among all products in the group. This method assumes that all products in the group are duplicates of each other based on their normalized names, and it creates a new Product instance to represent the merged result.
     * @param group the list of products that are considered duplicates and should be merged
     * @return a new Product instance with the name of the first product in the group and the lowest price among all products in the group, or null if the group is null or empty
     */
    public static Product mergeGroup(List<Product> group) {
        if (group == null || group.isEmpty()) {
            return null;
        }

        Product firstProduct = group.get(0);
        String mergedName = firstProduct.getName();
        if (containsHebrew(mergedName)) {
            for (Product product : group) {
                String candidateName = product.getName();
                if (!containsHebrew(candidateName)) {
                    mergedName = candidateName;
                    break;
                }
            }
        }

        Product cheapestProduct = firstProduct;

        for (Product product : group) {
            if (product.getPrice() < cheapestProduct.getPrice()) {
                cheapestProduct = product;
            }
        }

        return new Product(mergedName, cheapestProduct.getPrice());
    }
/**     * Deduplicates a list of products by grouping them based on their normalized names and merging each group into a single product with the lowest price. The deduplication process relies on the normalization of product names to create a canonical representation that can be used for grouping. After grouping, each group of duplicates is merged into a single product, and the resulting list contains only unique products with their lowest prices.
     * @param products the list of products to deduplicate
     * @return a list of deduplicated products, where each product represents a unique normalized name with the lowest price among its duplicates
     */
    public static List<Product> deduplicateProducts(List<Product> products) {
        Map<String, List<Product>> groups = groupDuplicates(products);
        List<Product> deduplicated = new ArrayList<>();

        for (List<Product> group : groups.values()) {
            Product merged = mergeGroup(group);
            if (merged != null) {
                deduplicated.add(merged);
            }
        }

        return deduplicated;
    }
    /** Normalizes a product name by applying several transformations to create a canonical representation that can be used for deduplication. The normalization process includes converting to lowercase, trimming whitespace, removing non-alphanumeric characters, collapsing multiple spaces, canonicalizing tokens based on exact mapping and fuzzy matching, and building a deterministic key that emphasizes brand and model identity while treating family tokens as optional when both brand and model are present. This comprehensive normalization helps to group similar products together even when there are variations in formatting, typos, or language.
     * @param input the original product name to normalize
     * @return the normalized product name that can be used as a key for grouping duplicates
     */
    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        String normalized = input.toLowerCase();
        normalized = normalized.trim();
        normalized = NON_LETTER_OR_NUMBER.matcher(normalized).replaceAll("");// remove punctuation and special characters, keeping only letters, numbers, and spaces
        normalized = normalized.replaceAll("\\s+", " ");// collapse multiple spaces into a single space
        normalized = canonicalizeTokens(normalized).trim();// canonicalize tokens and trim again to remove any extra spaces introduced during token canonicalization
        return buildCanonicalKey(normalized);// build a deterministic canonical key that emphasizes brand and model identity while treating family tokens as optional when both brand and model are present
    }
    /* Canonicalizes tokens in a normalized product name.
    * Exact token mapping is preferred; fuzzy correction is used only for likely typos.
    * @param normalized the normalized product name
    * @return the product name with canonicalized tokens
    */
    private static String canonicalizeTokens(String normalized) {
        if (normalized.isEmpty()) {
            return normalized;
        }

        String[] tokens = normalized.split(" ");
        StringBuilder builder = new StringBuilder();

        for (String token : tokens) {
            String canonical = resolveToken(token);
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(canonical);
        }

        return builder.toString();
    }

    /* Builds a deterministic canonical key with brand/model as identity core.
     * Family tokens are treated as optional only when brand and model both exist.
     */
    private static String buildCanonicalKey(String normalizedTokens) {
        if (normalizedTokens.isEmpty()) {
            return normalizedTokens;
        }

        String[] tokens = normalizedTokens.split(" ");
        String brand = null;
        for (String token : tokens) {
            if (isBrandToken(token)) {
                brand = token;
                break;
            }
        }

        String model = extractModelToken(tokens, brand);
        boolean hasBrandAndModel = brand != null && model != null;

        Set<String> family = new TreeSet<>();
        Set<String> descriptors = new TreeSet<>();

        for (String token : tokens) {
            if (token.equals(brand)) {
                continue;
            }

            String normalizedModel = normalizeModelToken(token, brand);
            if (normalizedModel != null && normalizedModel.equals(model)) {
                continue;
            }

            if (isFamilyToken(token)) {
                if (!hasBrandAndModel) {
                    family.add(token);
                }
                continue;
            }

            descriptors.add(token);
        }

        List<String> parts = new ArrayList<>();
        if (brand != null) {
            parts.add(brand);
        }
        if (model != null && brand != null) {
            parts.add(model);
        } else if (model != null) {
            descriptors.add(model);
        }
        parts.addAll(family);
        parts.addAll(descriptors);

        return String.join("|", parts);
    }

    private static boolean isBrandToken(String token) {
        return BRAND_TOKENS.contains(token);
    }

    private static boolean isFamilyToken(String token) {
        return FAMILY_TOKENS.contains(token);
    }

    private static String extractModelToken(String[] tokens, String brand) {
        for (String token : tokens) {
            String normalizedModel = normalizeModelToken(token, brand);
            if (normalizedModel != null) {
                return normalizedModel;
            }
        }
        return null;
    }
    /**
     * Normalizes a token to its canonical form based on the brand context. For Samsung products, tokens matching the pattern "s" followed by digits are normalized to just the digits, as this is a common way to refer to Samsung models (e.g., "S23" becomes "23"). For other brands and tokens, normalization relies on exact mapping and fuzzy matching in the resolveToken method. This method helps to build a more deterministic key by standardizing model tokens based on brand-specific patterns.
     * @param token the token to normalize
     * @param brand the identified brand token, which provides context for how to normalize the model token
     * @return the normalized model token if it matches known patterns for the brand, or null if it does not match any specific normalization rules (in which case it will be processed by resolveToken for potential fuzzy matching)
     */
    private static String normalizeModelToken(String token, String brand) {
        if (token.matches("\\d+")) {
            return token;
        }

        if ("samsung".equals(brand)) {
            java.util.regex.Matcher matcher = SAMSUNG_MODEL_PATTERN.matcher(token);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    /** Resolves a token to its canonical form using exact mapping and conditional fuzzy matching.
     * Exact mapping is applied first for efficiency and accuracy. Fuzzy matching is applied only to tokens that are not too short and do not contain digits, as these are more likely to produce false positives, especially in Hebrew where token ambiguity is higher. The method also distinguishes between Hebrew and English tokens to apply appropriate fuzzy matching criteria based on the script's characteristics.
     * @param token the token to resolve
     * @return the canonical form of the token if found, or the original token if no suitable canonical form is found
     */
    private static String resolveToken(String token) {
        String exact = TOKEN_CANONICAL_MAP.get(token);
        if (exact != null) {
            return exact;
        }
        boolean isHebrew = containsHebrew(token);
        if (shouldSkipFuzzy(token, isHebrew)) {
            return token;
        }

        Set<String> vocabulary = isHebrew ? HEBREW_TOKENS : ENGLISH_TOKENS;
        String bestMatch = findBestTokenMatch(token, vocabulary, isHebrew);
        if (bestMatch == null) {
            return token;
        }

        return TOKEN_CANONICAL_MAP.getOrDefault(bestMatch, bestMatch);
    }

    /**
     * Determines whether to skip fuzzy matching for a token based on its length and script. Short tokens and tokens containing digits are more likely to produce false positives in fuzzy matching, especially in Hebrew where token ambiguity is higher, so they are excluded from fuzzy correction.
     * @param token the token to evaluate
     * @param isHebrew indicates whether the token contains Hebrew characters, which affects the minimum length threshold for applying fuzzy matching due to higher ambiguity in Hebrew tokens
     * @return true if fuzzy matching should be skipped for the token, false otherwise
     */
    private static boolean shouldSkipFuzzy(String token, boolean isHebrew) {
        int minLength = isHebrew ? 4 : 3;
        if (token.length() < minLength) {
            return true;
        }

        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                return true;
            }
        }

        return false;
    }
    /**
     * Finds the best matching token from the vocabulary for a given token using fuzzy matching based on Levenshtein distance and similarity thresholds.
     * @param token the token to match
     * @param vocabulary the set of candidate tokens to match against
     * @param isHebrew indicates whether the token contains Hebrew characters, which affects the acceptance criteria for fuzzy matches due to higher ambiguity in Hebrew tokens
     * @return the best matching token from the vocabulary if a suitable match is found, or null if no acceptable match is found
     */
    private static String findBestTokenMatch(String token, Set<String> vocabulary, boolean isHebrew) {
        String bestMatch = null;
        double bestSimilarity = 0.0;
        int bestDistance = Integer.MAX_VALUE;

        for (String candidate : vocabulary) {
            if (candidate.equals(token)) {
                return candidate;
            }

            int distance = levenshteinDistance(token, candidate);
            int maxLength = Math.max(token.length(), candidate.length());
            double similarity = calculateSimilarity(token, candidate);
            boolean accepted;
            if (isHebrew) {
                accepted = distance <= maxAllowedDistance(maxLength);
            } else {
                double threshold = similarityThreshold(maxLength, false);
                accepted = similarity >= threshold || (maxLength <= 6 && distance <= 1);
            }

            if (!accepted) {
                continue;
            }

            if (similarity > bestSimilarity || (similarity == bestSimilarity && distance < bestDistance)) {
                bestSimilarity = similarity;
                bestDistance = distance;
                bestMatch = candidate;
            }
        }

        return bestMatch;
    }
    /**
     * Determines the similarity threshold for fuzzy matching based on token length and script.
     * @param tokenLength the length of the token being evaluated
     * @param isHebrew indicates whether the token contains Hebrew characters
     * @return the similarity threshold to use for accepting a fuzzy match, with stricter thresholds for shorter tokens and Hebrew tokens due to higher ambiguity and potential for false positives
     */
    private static double similarityThreshold(int tokenLength, boolean isHebrew) {
        if (isHebrew) {
            return 0.80;
        }
        if (tokenLength <= 5) {
            return 0.90;
        }
        return 0.85;
    }

    private static int maxAllowedDistance(int tokenLength) {
        if (tokenLength <= 5) {
            return 1;
        }
        return 2;
    }
    /**
     * Checks if a token contains any Hebrew characters.
     * @param token the token to check
     * @return true if the token contains at least one Hebrew character, false otherwise
     */
    private static boolean containsHebrew(String token) {
        for (int i = 0; i < token.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(token.charAt(i));
            if (block == Character.UnicodeBlock.HEBREW) {
                return true;
            }
        }
        return false;
    }
    /**
     * Creates a vocabulary set for either Hebrew or English tokens based on the TOKEN_CANONICAL_MAP.
     * @param hebrew if true, creates a vocabulary of Hebrew tokens; if false, creates a vocabulary of English tokens
     * @return a set of tokens in the specified script to be used for fuzzy matching
     */
    private static Set<String> createScriptVocabulary(boolean hebrew) {
        Set<String> vocabulary = new HashSet<>();

        for (String key : TOKEN_CANONICAL_MAP.keySet()) {
            if (containsHebrew(key) == hebrew) {
                vocabulary.add(key);
            }
        }

        for (String value : TOKEN_CANONICAL_MAP.values()) {
            if (containsHebrew(value) == hebrew) {
                vocabulary.add(value);
            }
        }

        return vocabulary;
    }
    /**
     * Creates a map of canonical tokens for normalization. This way we map Hebrew words to their English equivalents (when adding new products to the database, the corresponding canonical mappings should be added).
     * @return the map of canonical tokens
     */
    private static Map<String, String> createCanonicalMap() {
        Map<String, String> map = new HashMap<>();

        map.put("סמסונג", "samsung");
        map.put("גלקסי", "galaxy");
        map.put("גלאקסי", "galaxy");
        map.put("אייפון", "iphone");
        map.put("גוגל", "google");
        map.put("פיקסל", "pixel");
        map.put("אייפד", "ipad");
        map.put("איפד", "ipad");
        map.put("אייר", "air");
        map.put("פרו", "pro");
        return map;
    }

    /* Calculates similarity between two strings using Levenshtein distance.
     * Similarity = 1 - (levenshteinDistance / maxLength)
     * @param str1 first string
     * @param str2 second string
     * @return similarity score between 0 and 1
     */
    private static double calculateSimilarity(String str1, String str2) {
        int distance = levenshteinDistance(str1, str2);
        int maxLength = Math.max(str1.length(), str2.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - (double) distance / maxLength;
    }

    /* Calculates the Levenshtein distance (minimum edit distance) between two strings.
     * @param str1 first string
     * @param str2 second string
     * @return the minimum number of single-character edits required to transform str1 into str2
     */
    private static int levenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }

        return dp[str1.length()][str2.length()];
    }
    }