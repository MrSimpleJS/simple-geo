package de.mrsimplejs.simplegeo;

import java.math.BigInteger;

record IpRange(BigInteger start, BigInteger end, String cidr) {
    boolean contains(BigInteger value) {
        return value != null && value.compareTo(start) >= 0 && value.compareTo(end) <= 0;
    }
}
