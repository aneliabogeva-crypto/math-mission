package bg.mathmission.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mathmission")
public record AppProperties(
        String academicYear,
        String timezone,
        Consent consent,
        Session session,
        Seed seed,
        DevAccounts devAccounts,
        Cors cors,
        Integer authRateLimitPerMinute) {

    public int rateLimit() {
        return authRateLimitPerMinute == null ? 20 : authRateLimitPerMinute;
    }

    public record Consent(int requiredUnderAge, String textVersion) {}

    public record Session(int ttlHours) {}

    public record Seed(boolean enabled) {}

    public record DevAccounts(boolean enabled, String password, String adminTotpSecret) {}

    public record Cors(String allowedOrigins) {}

    public DevAccounts devAccountsOrDisabled() {
        return devAccounts == null ? new DevAccounts(false, null, null) : devAccounts;
    }
}
