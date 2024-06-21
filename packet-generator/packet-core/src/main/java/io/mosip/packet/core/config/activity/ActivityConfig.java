package io.mosip.packet.core.config.activity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "mosip.migrator.configuration")
public class ActivityConfig {

    @Getter
    @Setter
    private Map<String, Map<String, String>> activity;

    @Value("${mosip.migrator.configuration.activityName:DATA_CREATOR}")
    @Getter
    private String activityName;
}
