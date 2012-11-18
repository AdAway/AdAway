package org.donations;

public class DonationsConfig {
    
    public static final boolean DEBUG = false;

    /**
     * Flattr
     */
    public static final boolean FLATTR_ENABLED = true;
    public static final String FLATTR_PROJECT_URL = "http://code.google.com/p/ad-away/";
    // without http:// !
    public static final String FLATTR_URL = "flattr.com/thing/369138/AdAway-Ad-blocker-for-Android";

    /**
     * Google
     */
    public static final boolean GOOGLE_ENABLED = true;
    public static final String GOOGLE_PUBKEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAg8bTVFK5zIg4FGYkHKKQ/j/iGZQlXU0qkAv2BA6epOX1ihbMz78iD4SmViJlECHN8bKMHxouRNd9pkmQKxwEBHg5/xDC/PHmSCXFx/gcY/xa4etA1CSfXjcsS9i94n+j0gGYUg69rNkp+p/09nO9sgfRTAQppTxtgKaXwpfKe1A8oqmDUfOnPzsEAG6ogQL6Svo6ynYLVKIvRPPhXkq+fp6sJ5YVT5Hr356yCXlM++G56Pk8Z+tPzNjjvGSSs/MsYtgFaqhPCsnKhb55xHkc8GJ9haq8k3PSqwMSeJHnGiDq5lzdmsjdmGkWdQq2jIhKlhMZMm5VQWn0T59+xjjIIwIDAQAB";
    public static final String[] GOOGLE_CATALOG = new String[] { "adaway.donation.1",
            "adaway.donation.2", "adaway.donation.3", "adaway.donation.5", "adaway.donation.8",
            "adaway.donation.13" };

    /**
     * PayPal
     */
    public static final boolean PAYPAL_ENABLED = true;
    public static final String PAYPAL_USER = "dominik@dominikschuermann.de";
    public static final String PAYPAL_CURRENCY_CODE = "EUR";

    // TODO: get from values
    public static final String PAYPAL_ITEM_NAME = "AdAway Donation";

}
