package com.murisegames.api.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Prevent accidental use of a mixed or implicit runtime configuration. */
public class RuntimeProfileGuard implements EnvironmentPostProcessor, Ordered {
    private static final Set<String> RUNTIMES = Set.of("local", "staging", "prod", "test");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment.getActiveProfiles());
    }

    @Override
    public int getOrder() {
        // Fail before datasource/Flyway beans can open connections or run migrations.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    static void validate(String[] profiles) {
        if (Arrays.stream(profiles).filter(RUNTIMES::contains).count() != 1) {
            throw new IllegalStateException("Activate exactly one runtime profile: local, staging, prod, or test");
        }
    }
}
