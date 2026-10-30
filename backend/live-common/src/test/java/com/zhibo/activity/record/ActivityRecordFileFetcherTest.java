package com.zhibo.activity.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActivityRecordFileFetcherTest {

    @TempDir
    Path tempDir;

    @Test
    void extractLargestMp4_picksBiggest() throws Exception {
        Path zip = tempDir.resolve("pack.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            putEntry(zos, "small.mp4", "aaa");
            putEntry(zos, "nested/big.mp4", "bbbbbbbbbb");
            putEntry(zos, "readme.txt", "ignore");
        }
        Path extractDir = tempDir.resolve("out");
        Files.createDirectories(extractDir);

        Path mp4 = ActivityRecordFileFetcher.extractLargestMp4(zip, extractDir);

        assertEquals("big.mp4", mp4.getFileName().toString());
        assertEquals(10, Files.size(mp4));
        assertTrue(Files.notExists(extractDir.resolve("small.mp4")));
    }

    @Test
    void extractLargestMp4_noMp4_returnsNull() throws Exception {
        Path zip = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            putEntry(zos, "a.txt", "hello");
        }
        Path extractDir = tempDir.resolve("out2");
        Files.createDirectories(extractDir);

        assertNull(ActivityRecordFileFetcher.extractLargestMp4(zip, extractDir));
    }

    @Test
    void looksLikeZip_detectsPkHeader() throws Exception {
        Path zip = tempDir.resolve("t.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            putEntry(zos, "a.mp4", "x");
        }
        assertTrue(ActivityRecordFileFetcher.looksLikeZip(zip));
    }

    @Test
    void resolveToMp4_zipByMagic_noUrlSuffix() throws Exception {
        Path work = tempDir.resolve("w1");
        Files.createDirectories(work);
        Path downloaded = work.resolve("download.bin");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(downloaded))) {
            putEntry(zos, "nested/a.mp4", "small");
            putEntry(zos, "b.mp4", "larger!!");
        }

        Path mp4 = ActivityRecordFileFetcher.resolveToMp4(
                downloaded,
                "https://vod.vhall.example/download?id=1",
                "application/octet-stream",
                work);

        assertEquals("b.mp4", mp4.getFileName().toString());
        assertEquals(8, Files.size(mp4));
    }

    @Test
    void resolveToMp4_zipByContentType_noMagicConflict() throws Exception {
        // 魔数已是 zip；Content-Type 再确认
        Path work = tempDir.resolve("w2");
        Files.createDirectories(work);
        Path downloaded = work.resolve("download.bin");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(downloaded))) {
            putEntry(zos, "only.mp4", "video");
        }

        Path mp4 = ActivityRecordFileFetcher.resolveToMp4(
                downloaded,
                "https://cdn.example/x",
                "application/zip; charset=binary",
                work);

        assertEquals("only.mp4", mp4.getFileName().toString());
    }

    @Test
    void resolveToMp4_urlSaysZip_butMagicIsMp4_uploadsAsMp4() throws Exception {
        // 扩展名错：URL .zip，内容却是 ftyp mp4 → 魔数优先，不解压
        Path work = tempDir.resolve("w3");
        Files.createDirectories(work);
        Path downloaded = work.resolve("download.bin");
        Files.write(downloaded, new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '1'});

        Path mp4 = ActivityRecordFileFetcher.resolveToMp4(
                downloaded,
                "https://cdn.example/pack.ZIP",
                null,
                work);

        assertEquals("video.mp4", mp4.getFileName().toString());
        assertTrue(Files.isRegularFile(mp4));
    }

    @Test
    void resolveToMp4_zipUppercaseUrl_extracts() throws Exception {
        Path work = tempDir.resolve("w4");
        Files.createDirectories(work);
        Path downloaded = work.resolve("download.bin");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(downloaded))) {
            putEntry(zos, "clip.mp4", "data");
        }

        Path mp4 = ActivityRecordFileFetcher.resolveToMp4(
                downloaded,
                "https://cdn.example/Pack.ZIP",
                null,
                work);

        assertEquals("clip.mp4", mp4.getFileName().toString());
    }

    @Test
    void resolveToMp4_zipNoMp4_throws() throws Exception {
        Path work = tempDir.resolve("w5");
        Files.createDirectories(work);
        Path downloaded = work.resolve("download.bin");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(downloaded))) {
            putEntry(zos, "readme.txt", "no video");
        }

        IOException ex = assertThrows(IOException.class, () ->
                ActivityRecordFileFetcher.resolveToMp4(
                        downloaded,
                        "https://cdn.example/a.zip",
                        "application/zip",
                        work));
        assertTrue(ex.getMessage().contains("zip 中无 .mp4"));
    }

    @Test
    void contentTypeLooksLikeZip_variants() {
        assertTrue(ActivityRecordFileFetcher.contentTypeLooksLikeZip("application/zip"));
        assertTrue(ActivityRecordFileFetcher.contentTypeLooksLikeZip("APPLICATION/X-ZIP-COMPRESSED"));
        assertFalse(ActivityRecordFileFetcher.contentTypeLooksLikeZip("video/mp4"));
        assertFalse(ActivityRecordFileFetcher.contentTypeLooksLikeZip(null));
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
    }
}
