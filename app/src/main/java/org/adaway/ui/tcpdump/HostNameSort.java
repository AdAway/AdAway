package org.adaway.ui.tcpdump;

import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.Comparator;

/**
 * This enumerate represents the kind of sort available for host name.
 */
public enum HostNameSort {
    ALPHA_NUMERIC {
        @Override
        Comparator<String> comparator() {
            return String::compareTo;
        }
    },
    TOP_LEVEL_DOMAIN {
        @Override
        Comparator<String> comparator() {
            return (dns1, dns2) -> {
                String[] split1 = dns1.split("\\.");
                String[] split2 = dns2.split("\\.");

                int index1 = split1.length - 1;
                int index2 = split2.length - 1;

                while (index1 >= 0 && index2 >= 0) {
                    String part1 = split1[index1];
                    String part2 = split2[index2];

                    int partCompare = part1.compareTo(part2);
                    if (partCompare != 0) {
                        return partCompare;
                    }

                    index1--;
                    index2--;
                }

                return index1 < 0 ? -1 : +1;
            };
        }
    };

    /**
     * Get the sort comparator.
     *
     * @return The sort comparator.
     */
    abstract Comparator<String> comparator();
}
