package io.mosip.packet.core.config.login;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties(prefix = "token.request.login")
@Component
public class LoginConfig {

    @Getter
    @Setter
    private Map<String, Map<String, String>> credentials;
}
