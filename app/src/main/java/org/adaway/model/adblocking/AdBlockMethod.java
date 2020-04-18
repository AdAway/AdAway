package org.adaway.model.adblocking;

import java.util.Arrays;

/**
 * This enum represents the ad blocking methods.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public enum AdBlockMethod {
    /**
     * Not defined ad block method.
     */
    UNDEFINED(0),
    /**
     * The system hosts file ad block method.
     */
    ROOT(1),
    /**
     * The VPN based ad block method.
     */
    VPN(2);

    private int code;

    AdBlockMethod(int code) {
        this.code = code;
    }

    public static AdBlockMethod fromCode(int code) {
        return Arrays.stream(AdBlockMethod.values())
                .filter(method -> method.code == code)
                .findAny()
                .orElse(UNDEFINED);
    }

    public int toCode() {
        return this.code;
    }
}
