package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.security.JwtAuthenticationFilter;
import com.avivly.urlshortener.service.AnalyticsService;
import com.avivly.urlshortener.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
@AutoConfigureMockMvc(addFilters = false)
class RedirectControllerIpTest {

    @Autowired MockMvc mvc;
    @MockBean  LinkService linkService;
    @MockBean  AnalyticsService analyticsService;
    @MockBean  JwtAuthenticationFilter jwtAuthenticationFilter;

    private ShortLink activeLink() {
        return ShortLink.builder()
                .shortCode("abc")
                .originalUrl("https://example.com")
                .isActive(true)
                .totalClicks(0)
                .build();
    }

    @Test
    void xRealIpWinsWhenPresent() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        mvc.perform(get("/abc")
                .header("X-Real-IP", "1.2.3.4")
                .header("X-Forwarded-For", "9.9.9.9"))
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("1.2.3.4"));
    }

    @Test
    void xffRightToLeftFirstNonPrivate() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        // XFF: client → proxy1(private) → proxy2(public)
        // rightmost non-private is "5.6.7.8"
        mvc.perform(get("/abc")
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 5.6.7.8"))
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("5.6.7.8"));
    }

    @Test
    void bracketedIpv6InXffIsUnwrapped() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        // Bracketed notation with a private IPv4 hop to the right so the IPv6 wins
        mvc.perform(get("/abc")
                .header("X-Forwarded-For", "[2001:db8::1], 10.0.0.1"))
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("2001:db8::1"));
    }

    @Test
    void bracketedIpv6WithPortInXffIsUnwrapped() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        mvc.perform(get("/abc")
                .header("X-Forwarded-For", "[2001:db8::2]:8080"))
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("2001:db8::2"));
    }

    @Test
    void fallsBackToRemoteAddr() throws Exception {
        when(linkService.findByShortCode("abc")).thenReturn(activeLink());
        mvc.perform(get("/abc"))  // no proxy headers; MockMvc uses 127.0.0.1
           .andExpect(status().isFound());
        verify(analyticsService).logClickAsync(eq("abc"), any(), any(), eq("127.0.0.1"));
    }
}
