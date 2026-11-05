package net.jforum.user;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

import jakarta.servlet.http.HttpSession;

import net.jforum.config.ForumConfig;

import org.springframework.stereotype.Service;

/** Simple server-side image captcha (replacement for legacy JCaptcha in UserSession.createNewCaptcha). */
@Service
public class CaptchaService {

    static final String SESSION_KEY = "jforum.captcha";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final ForumConfig config;

    public CaptchaService(ForumConfig config) {
        this.config = config;
    }

    public String newChallenge(HttpSession session) {
        String text = randomText();
        session.setAttribute(SESSION_KEY, text);
        return text;
    }

    String randomText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** One-shot validation: the challenge is discarded whatever the outcome. */
    public boolean validate(HttpSession session, String response) {
        Object expected = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        if (!(expected instanceof String s) || response == null) {
            return false;
        }
        return config.getBool("captcha.ignore.case") ? s.equalsIgnoreCase(response.trim()) : s.equals(response.trim());
    }

    public BufferedImage render(String text) {
        int width = config.getInt("captcha.width", 250);
        int height = config.getInt("captcha.height", 50);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, width, height);
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(150 + random.nextInt(80), 150 + random.nextInt(80), 150 + random.nextInt(80)));
            g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
        }
        int fontSize = Math.min(height - 10, 34);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        int step = (width - 20) / Math.max(1, text.length());
        for (int i = 0; i < text.length(); i++) {
            g.setColor(new Color(random.nextInt(90), random.nextInt(90), 60 + random.nextInt(120)));
            double angle = (random.nextDouble() - 0.5) * 0.5;
            int x = 10 + i * step;
            int y = height / 2 + fontSize / 3;
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(text.charAt(i)), x, y);
            g.rotate(-angle, x, y);
        }
        g.dispose();
        return img;
    }
}
