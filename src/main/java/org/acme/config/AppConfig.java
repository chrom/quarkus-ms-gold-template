package org.acme.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    String name();

    @WithDefault("v1")
    String version();

    Features features();

    interface Features {
        boolean experimental();
        List<String> enabledList();
    }
}
