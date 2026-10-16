package github.atharv3221.crewsync.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("jwt")
public record JwtProperties(String secret, @DefaultValue("7d") Duration ttl) {
}
