package net.jforum.user;

import java.io.IOException;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Legacy module "captcha": /captcha/generate[/{timestamp}].page renders a fresh challenge. */
@Controller
public class CaptchaController {

    private final CaptchaService captcha;

    public CaptchaController(CaptchaService captcha) {
        this.captcha = captcha;
    }

    @GetMapping({"/captcha/generate.page", "/captcha/generate/{timestamp}.page"})
    public void generate(@PathVariable(required = false) String timestamp, HttpSession session, HttpServletResponse response)
        throws IOException {
        String text = captcha.newChallenge(session);
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store");
        ImageIO.write(captcha.render(text), "png", response.getOutputStream());
    }
}
