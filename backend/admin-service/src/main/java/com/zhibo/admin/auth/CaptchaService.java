package com.zhibo.admin.auth;

import com.zhibo.admin.auth.dto.CaptchaResponse;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/** 图形验证码（内存 + TTL；不区分大小写）。 */
@Service
public class CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 4;
    private static final long TTL_SECONDS = 300;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public CaptchaService(Clock clock) {
        this.clock = clock;
    }

    public CaptchaResponse create() {
        purge();
        String id = UUID.randomUUID().toString();
        String code = randomCode();
        store.put(id, new Entry(code, clock.instant().plusSeconds(TTL_SECONDS)));
        return new CaptchaResponse(id, "data:image/png;base64," + renderPngBase64(code));
    }

    /** 校验成功则消费；失败或过期抛 400。 */
    public void verifyAndConsume(String captchaId, String captchaCode) {
        purge();
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        Entry entry = store.remove(captchaId);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        if (!entry.code().equalsIgnoreCase(captchaCode.trim())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String renderPngBase64(String code) {
        int w = 120;
        int h = 40;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, w, h);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(40 + random.nextInt(80), 40 + random.nextInt(80), 40 + random.nextInt(80)));
                g.drawString(String.valueOf(code.charAt(i)), 18 + i * 24, 28 + random.nextInt(5));
            }
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(160 + random.nextInt(60), 160 + random.nextInt(60), 160 + random.nextInt(60)));
                g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
            }
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("生成验证码失败", ex);
        }
    }

    private void purge() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }

    private record Entry(String code, Instant expiresAt) {}
}
