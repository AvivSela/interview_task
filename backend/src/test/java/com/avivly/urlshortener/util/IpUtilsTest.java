package com.avivly.urlshortener.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilsTest {

    private static boolean isPrivate(String ip) throws UnknownHostException {
        return IpUtils.isPrivateAddress(InetAddress.getByName(ip));
    }

    @Test
    void rfc1918RangesArePrivate() throws Exception {
        assertThat(isPrivate("10.0.0.1")).isTrue();
        assertThat(isPrivate("172.16.0.1")).isTrue();
        assertThat(isPrivate("172.31.255.255")).isTrue();
        assertThat(isPrivate("192.168.1.1")).isTrue();
    }

    @Test
    void loopbackIsPrivate() throws Exception {
        assertThat(isPrivate("127.0.0.1")).isTrue();
        assertThat(isPrivate("::1")).isTrue();
    }

    @Test
    void cgnatRangeIsPrivate() throws Exception {
        assertThat(isPrivate("100.64.0.1")).isTrue();
        assertThat(isPrivate("100.100.50.50")).isTrue();
        assertThat(isPrivate("100.127.255.255")).isTrue();
    }

    @Test
    void cgnatBoundariesArePublic() throws Exception {
        assertThat(isPrivate("100.63.255.255")).isFalse();
        assertThat(isPrivate("100.128.0.0")).isFalse();
    }

    @Test
    void publicIpsAreNotPrivate() throws Exception {
        assertThat(isPrivate("8.8.8.8")).isFalse();
        assertThat(isPrivate("1.1.1.1")).isFalse();
        assertThat(isPrivate("81.2.69.142")).isFalse();
    }
}
