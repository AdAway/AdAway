/*
 * Copyright (C) 2011 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.donations;

public class DonationsConfiguration {

    public static final String TAG = "Donations";

    public static final boolean DEBUG = false;

    /** Flattr */

    public static final String FLATTR_PROJECT_URL = "http://code.google.com/p/ad-away/";
    public static final String FLATTR_URL = "http://flattr.com/thing/369138/AdAway-Ad-blocker-for-Android";

    /** PayPal */

    public static final String PAYPAL_USER = "dominik@dominikschuermann.de";
    public static final String PAYPAL_ITEM_NAME = "AdAway Donation";
    public static final String PAYPAL_CURRENCY_CODE = "EUR";

    /** Google Play Store In-App Billing */

    // your public key from the google play publisher account
    public static final String GOOGLE_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAg8bTVFK5zIg4FGYkHKKQ/j/iGZQlXU0qkAv2BA6epOX1ihbMz78iD4SmViJlECHN8bKMHxouRNd9pkmQKxwEBHg5/xDC/PHmSCXFx/gcY/xa4etA1CSfXjcsS9i94n+j0gGYUg69rNkp+p/09nO9sgfRTAQppTxtgKaXwpfKe1A8oqmDUfOnPzsEAG6ogQL6Svo6ynYLVKIvRPPhXkq+fp6sJ5YVT5Hr356yCXlM++G56Pk8Z+tPzNjjvGSSs/MsYtgFaqhPCsnKhb55xHkc8GJ9haq8k3PSqwMSeJHnGiDq5lzdmsjdmGkWdQq2jIhKlhMZMm5VQWn0T59+xjjIIwIDAQAB";
    // mapping from the possible donations of 1,2,3,5,8 and 13 eur to your in-app items defined in
    // the publisher account of google play
    public static final String[] GOOGLE_CATALOG = new String[] { "adaway.donation.1",
            "adaway.donation.2", "adaway.donation.3", "adaway.donation.5", "adaway.donation.8",
            "adaway.donation.13" };
}
