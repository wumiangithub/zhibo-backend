package com.zhibo.activity.record;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 从微吼 {@code download_url} 拉成片到临时目录；zip 则解压并只保留体积最大的 {@code .mp4}。
 */
@Component
@Slf4j
public class ActivityRecordFileFetcher {

    /** 连接超时。 */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** 整次下载超时（大成片）。 */
    static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(20);

    /** 微吼/国内工具常见非 UTF-8 zip 条目名。 */
    private static final Charset ZIP_CHARSET_FALLBACK = Charset.forName("GBK");

    private final HttpClient httpClient;

    public ActivityRecordFileFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** 单测可注入 mock/自定义 client。 */
    ActivityRecordFileFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 下载 URL 到 {@code workDir}，必要时解压，返回待上传的 mp4 路径。
     * 调用方负责清理 {@code workDir}。
     *
     * @throws IOException 下载/解压失败或无可用 mp4
     */
    public Path fetchMp4(String downloadUrl, Path workDir) throws IOException, InterruptedException {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IOException("download_url 为空");
        }
        Files.createDirectories(workDir);
        Path downloaded = workDir.resolve("download.bin");
        String contentType = downloadTo(downloadUrl.trim(), downloaded);

        return resolveToMp4(downloaded, downloadUrl, contentType, workDir);
    }

    /**
     * 魔数优先：PK → 解压；ftyp → 当 mp4；否则再信 Content-Type / URL 后缀。
     * 微吼链常无 {@code .zip} 后缀，故不能只靠 URL。
     */
    static Path resolveToMp4(Path downloaded, String downloadUrl, String contentType, Path workDir)
            throws IOException {
        boolean magicZip = looksLikeZip(downloaded);
        boolean magicMp4 = looksLikeMp4(downloaded);
        boolean metaZip = contentTypeLooksLikeZip(contentType) || urlLooksLikeZip(downloadUrl);
        boolean metaMp4 = contentTypeLooksLikeMp4(contentType) || urlLooksLikeMp4(downloadUrl);

        if (magicZip || (!magicMp4 && metaZip)) {
            log.info("回放文件识别为 zip，开始解压 magicZip={} metaZip={} contentType={}",
                    magicZip, metaZip, contentType);
            Path extractDir = workDir.resolve("extract");
            Files.createDirectories(extractDir);
            Path mp4 = extractLargestMp4(downloaded, extractDir);
            if (mp4 == null) {
                throw new IOException("zip 中无 .mp4");
            }
            log.info("回放解压完成，取最大 mp4 name={} bytes={}",
                    mp4.getFileName(), Files.size(mp4));
            return mp4;
        }
        if (magicMp4 || metaMp4) {
            Path mp4 = workDir.resolve("video.mp4");
            Files.move(downloaded, mp4, StandardCopyOption.REPLACE_EXISTING);
            log.info("回放文件识别为 mp4，跳过解压 magicMp4={} metaMp4={} bytes={}",
                    magicMp4, metaMp4, Files.size(mp4));
            return mp4;
        }
        throw new IOException("下载内容既非 zip 也非 mp4"
                + (contentType != null && !contentType.isBlank() ? " contentType=" + contentType : ""));
    }

    private String downloadTo(String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("下载 HTTP " + code);
        }
        if (!Files.isRegularFile(target) || Files.size(target) <= 0) {
            throw new IOException("下载文件为空");
        }
        return response.headers().firstValue("Content-Type").orElse(null);
    }

    /**
     * 解压 zip，只保留体积最大的 {@code .mp4}；无则返回 null。
     * 防 Zip Slip；条目名 UTF-8 失败时整包回退 GBK。
     */
    static Path extractLargestMp4(Path zipFile, Path extractDir) throws IOException {
        try {
            return extractLargestMp4WithCharset(zipFile, extractDir, StandardCharsets.UTF_8);
        } catch (ZipException utf8Ex) {
            log.info("zip UTF-8 解压失败，回退 GBK: {}", utf8Ex.toString());
            try {
                return extractLargestMp4WithCharset(zipFile, extractDir, ZIP_CHARSET_FALLBACK);
            } catch (IOException gbkEx) {
                gbkEx.addSuppressed(utf8Ex);
                throw new IOException("解压失败: " + gbkEx.getMessage(), gbkEx);
            }
        }
    }

    private static Path extractLargestMp4WithCharset(Path zipFile, Path extractDir, Charset charset)
            throws IOException {
        Path largest = null;
        long largestSize = -1L;
        try (ZipFile zf = new ZipFile(zipFile.toFile(), charset)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                    continue;
                }
                String simple = Path.of(name).getFileName().toString();
                if (simple.isBlank()) {
                    continue;
                }
                Path out = extractDir.resolve(simple).normalize();
                if (!out.startsWith(extractDir.normalize())) {
                    log.warn("跳过可疑 zip 路径 entry={}", name);
                    continue;
                }
                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                long size = Files.size(out);
                if (size > largestSize) {
                    if (largest != null && !largest.equals(out)) {
                        Files.deleteIfExists(largest);
                    }
                    largestSize = size;
                    largest = out;
                } else {
                    Files.deleteIfExists(out);
                }
            }
        }
        return largest;
    }

    static boolean looksLikeZip(Path path) throws IOException {
        byte[] mag = readPrefix(path, 4);
        // PK\x03\x04 local / PK\x05\x06 empty EOCD / PK\x07\x08 spanned
        return mag.length >= 4
                && mag[0] == 0x50
                && mag[1] == 0x4B
                && (mag[2] == 0x03 || mag[2] == 0x05 || mag[2] == 0x07)
                && (mag[3] == 0x04 || mag[3] == 0x06 || mag[3] == 0x08);
    }

    static boolean looksLikeMp4(Path path) throws IOException {
        byte[] buf = readPrefix(path, 8);
        return buf.length >= 8
                && buf[4] == 'f'
                && buf[5] == 't'
                && buf[6] == 'y'
                && buf[7] == 'p';
    }

    static boolean contentTypeLooksLikeZip(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        return ct.equals("application/zip")
                || ct.equals("application/x-zip-compressed")
                || ct.equals("multipart/x-zip")
                || ct.equals("application/zip-compressed");
    }

    static boolean contentTypeLooksLikeMp4(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        return ct.equals("video/mp4") || ct.equals("application/mp4");
    }

    static boolean urlLooksLikeZip(String url) {
        String path = urlPath(url);
        return path.endsWith(".zip");
    }

    static boolean urlLooksLikeMp4(String url) {
        String path = urlPath(url);
        return path.endsWith(".mp4");
    }

    private static String urlPath(String url) {
        try {
            String path = URI.create(url.trim()).getPath();
            return path == null ? "" : path.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return url.toLowerCase(Locale.ROOT);
        }
    }

    private static byte[] readPrefix(Path path, int n) throws IOException {
        byte[] buf = new byte[n];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.read(buf);
            if (read < n) {
                byte[] shortBuf = new byte[Math.max(read, 0)];
                if (read > 0) {
                    System.arraycopy(buf, 0, shortBuf, 0, read);
                }
                return shortBuf;
            }
        }
        return buf;
    }
}
