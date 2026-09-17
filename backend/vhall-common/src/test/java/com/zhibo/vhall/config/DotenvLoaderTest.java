package com.zhibo.vhall.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
