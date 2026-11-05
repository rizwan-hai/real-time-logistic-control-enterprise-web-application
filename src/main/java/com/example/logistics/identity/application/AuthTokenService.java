package com.example.logistics.identity.application;

import com.example.logistics.identity.config.IdentityProperties;
import com.example.logistics.identity.domain.UserAccountEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthTokenService {
    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;
    private final PermissionResolutionService permissionResolution;

    public AuthTokenService(JwtEncoder encoder, IdentityProperties properties, Clock clock, PermissionResolutionService permissionResolution) {
        this.encoder = encoder; this.properties = properties; this.clock = clock; this.permissionResolution = permissionResolution;
    }

    public String accessToken(UserAccountEntity user, UUID familyId) {
        Instant now = clock.instant();
        var authorities = permissionResolution.resolve(user);
        var claims = baseClaims(user, now, now.plusSeconds(properties.accessTokenMinutes() * 60L))
                .claim("authorities", authorities)
                .claim("purpose", "access")
                .claim("organizationId", user.getOrganizationId().toString())
                .claim("familyId", familyId.toString());
        if (user.getCustomerId() != null) claims.claim("customerId", user.getCustomerId().toString());
        if (user.getDriverId() != null) claims.claim("driverId", user.getDriverId().toString());
        claims.claim("warehouseIds", permissionResolution.warehouseScopes(user));
        return encode(claims.build());
    }

    public String mfaChallenge(UserAccountEntity user) {
        Instant now = clock.instant();
        return encode(baseClaims(user, now, now.plusSeconds(300)).claim("purpose", "mfa_challenge").build());
    }

    public String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private JwtClaimsSet.Builder baseClaims(UserAccountEntity user, Instant now, Instant expires) {
        return JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(user.getId().toString()).id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(expires);
    }
}
