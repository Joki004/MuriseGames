package com.murisegames.api.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Prevent accidental use of a mixed or implicit runtime configuration. */
@Component
public class RuntimeProfileGuard implements InitializingBean {
    private static final Set<String> RUNTIMES = Set.of("local", "staging", "prod", "test");
    private final Environment environment;

    public RuntimeProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(environment.getActiveProfiles());
    }

    static void validate(String[] profiles) {
        if (Arrays.stream(profiles).filter(RUNTIMES::contains).count() != 1) {
            throw new IllegalStateException("Activate exactly one runtime profile: local, staging, prod, or test");
        }
    }
}
