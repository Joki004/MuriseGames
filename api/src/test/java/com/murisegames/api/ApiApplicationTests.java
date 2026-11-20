package com.murisegames.api;

import com.murisegames.api.modules.User.PlatformRoleGrantRepository;
import com.murisegames.api.modules.User.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@ActiveProfiles("test")
class ApiApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlatformRoleGrantRepository platformRoleGrantRepository;

    @MockitoBean
    private EntityManager entityManager;

    @Test
    void contextLoads() {
    }
}