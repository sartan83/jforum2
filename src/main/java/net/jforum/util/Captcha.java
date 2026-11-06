package net.jforum.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Random;

import javax.imageio.ImageIO;

import net.jforum.JForumExecutionContext;
import net.jforum.SessionFacade;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.apache.log4j.Logger;

/**
 * Image captcha (replaces the JCaptcha based engine). Honors the original
 * captcha.* settings: width, height, min/max words, min/max font size and
 * the "ignore case" flag.
 *
 * @author Rafael Steil
 */
public class Captcha
{
	private static final Logger logger = Logger.getLogger(Captcha.class);
	private static final Captcha classInstance = new Captcha();
	private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

	private final Random random = new SecureRandom();

	public static Captcha getInstance()
	{
		return classInstance;
	}

	/**
	 * A captcha challenge: the generated image and its solution.
	 */
	public static class Challenge
	{
		private final String text;
		private final BufferedImage image;

		Challenge(String text, BufferedImage image)
		{
			this.text = text;
			this.image = image;
		}

		public BufferedImage getImage()
		{
			return this.image;
		}

		public String getText()
		{
			return this.text;
		}

		public boolean validateResponse(String response)
		{
			if (response == null) {
				return false;
			}

			return SystemGlobals.getBoolValue(ConfigKeys.CAPTCHA_IGNORE_CASE)
				? this.text.equalsIgnoreCase(response.trim())
				: this.text.equals(response.trim());
		}
	}

	public Challenge getNextImageCaptcha()
	{
		int minWords = intValue(ConfigKeys.CAPTCHA_MIN_WORDS, 4);
		int maxWords = Math.max(minWords, intValue(ConfigKeys.CAPTCHA_MAX_WORDS, 6));
		int length = minWords + this.random.nextInt(maxWords - minWords + 1);

		StringBuilder sb = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			sb.append(CHARS.charAt(this.random.nextInt(CHARS.length())));
		}

		String text = sb.toString();
		return new Challenge(text, this.render(text));
	}

	private BufferedImage render(String text)
	{
		int width = intValue(ConfigKeys.CAPTCHA_WIDTH, 250);
		int height = intValue(ConfigKeys.CAPTCHA_HEIGHT, 75);
		int minFont = intValue(ConfigKeys.CAPTCHA_MIN_FONT_SIZE, 25);
		int maxFont = Math.max(minFont, intValue(ConfigKeys.CAPTCHA_MAX_FONT_SIZE, 35));

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);

		for (int i = 0; i < 12; i++) {
			g.setColor(new Color(150 + this.random.nextInt(100), 150 + this.random.nextInt(100), 150 + this.random.nextInt(100)));
			g.setStroke(new BasicStroke(1 + this.random.nextInt(2)));
			g.drawLine(this.random.nextInt(width), this.random.nextInt(height), this.random.nextInt(width), this.random.nextInt(height));
		}

		int x = 10;
		int step = Math.max(1, (width - 20) / text.length());

		for (int i = 0; i < text.length(); i++) {
			int size = minFont + this.random.nextInt(maxFont - minFont + 1);
			Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
			AffineTransform rotation = AffineTransform.getRotateInstance((this.random.nextDouble() - 0.5) * 0.6);
			g.setFont(font.deriveFont(rotation));
			g.setColor(new Color(this.random.nextInt(120), this.random.nextInt(120), this.random.nextInt(120)));
			g.drawString(String.valueOf(text.charAt(i)), x, height / 2 + size / 3);
			x += step;
		}

		g.dispose();
		return image;
	}

	private static int intValue(String key, int defaultValue)
	{
		String value = SystemGlobals.getValue(key);

		try {
			return value == null ? defaultValue : Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public void writeCaptchaImage()
	{
		BufferedImage image = SessionFacade.getUserSession().getCaptchaImage();

		if (image == null) {
			return;
		}

		try (OutputStream outputStream = JForumExecutionContext.getResponse().getOutputStream()) {
			ImageIO.write(image, "jpg", outputStream);
		}
		catch (IOException ex) {
			logger.error(ex);
		}
	}
}
