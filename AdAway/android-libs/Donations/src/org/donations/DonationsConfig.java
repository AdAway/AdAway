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
    public static final boolean GOOGLE_ENABLED = false;
    public static final String GOOGLE_PUBKEY = null;
    public static final String[] GOOGLE_CATALOG = null;

    /**
     * PayPal
     */
    public static final boolean PAYPAL_ENABLED = true;
    public static final String PAYPAL_USER = "dominik@dominikschuermann.de";
    public static final String PAYPAL_CURRENCY_CODE = "EUR";

    // TODO: get from values
    public static final String PAYPAL_ITEM_NAME = "AdAway Donation";

}
