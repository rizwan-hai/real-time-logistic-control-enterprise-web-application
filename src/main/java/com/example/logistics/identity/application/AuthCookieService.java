package com.example.logistics.identity.application;

import com.example.logistics.identity.config.IdentityProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class AuthCookieService {
    public static final String REFRESH_COOKIE = "LOGISTICS_REFRESH";
    public static final String COOKIE_PATH = "/api/v1/auth";
    private final IdentityProperties properties;
    public AuthCookieService(IdentityProperties properties) { this.properties = properties; }

    public void setRefresh(HttpServletResponse response, String value) {
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true)
                .secure(properties.secureCookies()).sameSite("Strict").path(COOKIE_PATH)
                .maxAge(Duration.ofDays(properties.refreshTokenDays())).build().toString());
    }
    public void clearRefresh(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true)
                .secure(properties.secureCookies()).sameSite("Strict").path(COOKIE_PATH).maxAge(Duration.ZERO).build().toString());
    }
}
