package io.civicpulse.identity.application;

import io.civicpulse.identity.SignInFailed;
import io.civicpulse.identity.UserRegistered;
import io.civicpulse.identity.UserSignedIn;
import io.civicpulse.identity.domain.EmailAddresses;
import io.civicpulse.identity.domain.User;
import io.civicpulse.identity.infrastructure.UserRepository;
import io.civicpulse.shared.Ids;
import io.civicpulse.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // bcrypt limit
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String unknownUserHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens,
            RefreshTokenService refreshTokens, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.clock = clock;
        this.unknownUserHash = passwordEncoder.encode(Ids.newId().toString());
    }

    @Transactional
    public AuthResult register(String rawEmail, String password, String displayName) {
        String email = EmailAddresses.normalize(rawEmail);
        if (users.existsByEmail(email)) {
            throw new ApiException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        Instant now = clock.instant();
        User user = User.register(Ids.newId(), email, passwordEncoder.encode(password), displayName.strip(), now);
        user.recordSuccessfulLogin(now);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        events.publishEvent(new UserRegistered(user.id(), email, now));
        return signIn(user, now);
    }

    // keep the failed attempt count
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String rawEmail, String password) {
        String email = EmailAddresses.normalize(rawEmail);
        Instant now = clock.instant();
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw invalidCredentials();
        }

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, unknownUserHash);
            throw invalidCredentials();
        }

        User user = found.get();
        boolean passwordMatches = passwordEncoder.matches(password, user.passwordHash());
        if (!user.canSignInAt(now)) {
            throw invalidCredentials();
        }
        if (!passwordMatches) {
            boolean locked = user.recordFailedLogin(now);
            events.publishEvent(new SignInFailed(user.id(), email, locked, now));
            throw invalidCredentials();
        }

        user.recordSuccessfulLogin(now);
        events.publishEvent(new UserSignedIn(user.id(), email, now));
        return signIn(user, now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        }
        Instant now = clock.instant();
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, now);
        User user = users.findById(rotation.userId())
                .filter(User::isEnabled)
                .orElse(null);
        if (user == null) {
            refreshTokens.revokeFamily(rotation.familyId(), now);
            throw new ApiException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new AuthResult(
                accessTokens.issue(user, now), accessTokens.ttlSeconds(), rotation.refreshToken(), UserProfile.of(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revokeFamilyOf(rawRefreshToken, clock.instant());
        }
    }

    @Transactional(readOnly = true)
    public UserProfile profile(UUID userId) {
        return users.findById(userId)
                .map(UserProfile::of)
                .orElseThrow(() -> new ApiException(IdentityErrorCode.USER_NOT_FOUND));
    }

    private AuthResult signIn(User user, Instant now) {
        String refreshToken = refreshTokens.startFamily(user.id(), now);
        return new AuthResult(accessTokens.issue(user, now), accessTokens.ttlSeconds(), refreshToken, UserProfile.of(user));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(IdentityErrorCode.INVALID_CREDENTIALS);
    }
}
