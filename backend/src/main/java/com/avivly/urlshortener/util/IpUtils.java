package com.avivly.urlshortener.util;

import java.net.InetAddress;

public final class IpUtils {
    private IpUtils() {}

    public static boolean isPrivateAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
            return true;
        }
        // RFC 6598 Shared Address Space (carrier-grade NAT): 100.64.0.0/10
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int first  = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            if (first == 100 && (second & 0xC0) == 0x40) {
                return true;
            }
        }
        return false;
    }
}
