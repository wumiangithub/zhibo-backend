package com.zhibo.vhall.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

class DotenvLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadParsesKeyValueSkipsCommentsAndQuotes() throws Exception {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # comment
                VHALL_APP_KEY=abc123
                VHALL_APP_SECRET="sec ret"
                EMPTY=
                badline
                """);

        Map<String, Object> map = DotenvLoader.load(env);

        assertEquals("abc123", map.get("VHALL_APP_KEY"));
        assertEquals("sec ret", map.get("VHALL_APP_SECRET"));
        assertEquals("", map.get("EMPTY"));
        assertTrue(!map.containsKey("badline"));
    }

    @Test
    void injectsVhallPropertiesFromDotenv() throws Exception {
        Files.writeString(tempDir.resolve(".env"), """
                VHALL_APP_KEY=abc123
                VHALL_APP_SECRET=s3cret
                """);
        String previous = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
        try {
            StandardEnvironment environment = new StandardEnvironment();
            new DotenvEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
            assertEquals("abc123", environment.getProperty("VHALL_APP_KEY"));
            assertEquals("abc123", environment.getProperty("vhall.app-key"));
            assertEquals("s3cret", environment.getProperty("vhall.app-secret"));
        } finally {
            System.setProperty("user.dir", previous);
        }
    }
}
