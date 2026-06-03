package com.memcyco.urlshortener.util;

import java.net.InetAddress;

public final class IpUtils {
    private IpUtils() {}

    public static boolean isPrivateAddress(InetAddress addr) {
        return addr.isLoopbackAddress()
            || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress();
    }
}
