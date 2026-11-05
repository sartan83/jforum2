package net.jforum.user;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Banlist;
import net.jforum.domain.User;
import net.jforum.format.PostFormatter;
import net.jforum.format.SafeHtml;
import net.jforum.i18n.I18n;
import net.jforum.repository.BanlistRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.AnonymousUser;
import net.jforum.security.PermissionService;
import net.jforum.support.SequenceService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * User registration, activation, password recovery and profile updates
 * (legacy UserAction, UserCommon.saveUser, UserDAO).
 */
@Service
public class UserService {

    static final String USERNAME_MAX_LENGTH = "username.max.length";
    static final String MAIL_ACTIVATION_SUBJECT = "mail.activationKey.subject";
    static final String MAIL_LOST_PASSWORD_SUBJECT = "mail.lostPassword.subject";
    static final String MAIL_WELCOME_SUBJECT = "mail.welcome.subject";

    private final UserRepository users;
    private final BanlistRepository banlist;
    private final SequenceService sequences;
    private final PasswordEncoder passwordEncoder;
    private final ForumConfig config;
    private final ForumMailer mailer;
    private final PermissionService permissions;
    private final I18n i18n;
    private final PostFormatter formatter;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository users, BanlistRepository banlist, SequenceService sequences, PasswordEncoder passwordEncoder,
                       ForumConfig config, ForumMailer mailer, PermissionService permissions, I18n i18n,
                       PostFormatter formatter) {
        this.users = users;
        this.banlist = banlist;
        this.sequences = sequences;
        this.passwordEncoder = passwordEncoder;
        this.config = config;
        this.mailer = mailer;
        this.permissions = permissions;
        this.i18n = i18n;
        this.formatter = formatter;
    }

    public Optional<User> findById(Integer id) {
        return id == null ? Optional.empty() : users.findById(id);
    }

    /** Validation of username/email shared by registration and the REST API. Returns an i18n error or null. */
    public String validateNewUser(String username, String email) {
        if (username.length() > config.getInt(USERNAME_MAX_LENGTH, 25)) {
            return i18n.getMessage("User.usernameTooBig");
        }
        if (username.indexOf('<') > -1 || username.indexOf('>') > -1) {
            return i18n.getMessage("User.usernameInvalidChars");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            return i18n.getMessage("UsernameExists");
        }
        if (email != null && !email.isBlank() && users.findByEmailIgnoreCase(email.trim()).isPresent()) {
            return i18n.getMessage("User.emailExists", email);
        }
        return null;
    }

    /** Legacy BanlistRepository.shouldBan for e-mail ("*@domain" wildcards) and IP ("192.168.*.*" wildcards). */
    public boolean isBanned(String email, String ip) {
        for (Banlist b : banlist.findAll()) {
            if (b.getEmail() != null && email != null && wildcardMatch(b.getEmail(), email)) {
                return true;
            }
            if (b.getIp() != null && ip != null && wildcardMatch(b.getIp(), ip)) {
                return true;
            }
        }
        return false;
    }

    static boolean wildcardMatch(String pattern, String value) {
        String regex = ("\\Q" + pattern.trim().toLowerCase() + "\\E").replace("*", "\\E.*\\Q");
        return value.trim().toLowerCase().matches(regex);
    }

    /** Creates the user (legacy UserDAO.addNew + writeUserActive). {@code rawPassword} is hashed here. */
    public User create(String username, String rawPassword, String email, boolean requiresActivation) {
        return createWithHash(username, passwordEncoder.encode(rawPassword), email, requiresActivation);
    }

    public User createWithHash(String username, String passwordHash, String email, boolean requiresActivation) {
        User u = new User();
        u.setId(sequences.next("users"));
        u.setUsername(username);
        u.setPassword(passwordHash);
        u.setEmail(email == null ? null : email.trim());
        u.setRegistrationDate(Instant.now());
        u.setLastVisit(Instant.now());
        u.setRankId(0);
        u.getGroupIds().add(config.getInt(ConfigKeys.DEFAULT_USER_GROUP, AnonymousUser.GENERAL_GROUP_ID));
        if (requiresActivation) {
            u.setActive(false);
            u.setActivationKey(randomHash());
        }
        users.save(u);
        return u;
    }

    public void sendActivationMail(User u) {
        mailer.send(u.getEmail(), MAIL_ACTIVATION_SUBJECT, "activateAccount.txt", Map.of(
            "user", u,
            "url", mailer.absoluteUrl("/user/activateAccount/" + u.getActivationKey() + "/" + u.getId() + ".page"),
            "manualUrl", mailer.absoluteUrl("/user/activateManual.page")));
    }

    public void sendWelcomeMail(User u) {
        mailer.send(u.getEmail(), MAIL_WELCOME_SUBJECT, "welcome.txt", Map.of(
            "user", u, "url", mailer.absoluteUrl("/user/login.page")));
    }

    /** Legacy UserDAO.validateActivationKeyHash + writeUserActive. */
    public Optional<User> activate(Integer userId, String hash) {
        Optional<User> user = findById(userId)
            .filter(u -> u.getActivationKey() != null && hash != null && u.getActivationKey().equals(hash.trim()));
        user.ifPresent(u -> {
            u.setActive(true);
            u.setActivationKey(null);
            users.save(u);
        });
        return user;
    }

    /** Legacy UserAction.prepareLostPassword: finds the user by e-mail or username and writes a security hash. */
    public Optional<User> prepareLostPassword(String username, String email) {
        Optional<User> user = Optional.empty();
        if (email != null && !email.isBlank()) {
            user = users.findByEmailIgnoreCase(email.trim());
        }
        if (user.isEmpty() && username != null && !username.isBlank()) {
            user = users.findByUsernameIgnoreCase(username.trim());
        }
        user = user.filter(u -> !u.isDeleted() && u.getId() != AnonymousUser.ID && u.getEmail() != null && !u.getEmail().isBlank());
        user.ifPresent(u -> {
            u.setSecurityHash(randomHash());
            users.save(u);
            mailer.send(u.getEmail(), MAIL_LOST_PASSWORD_SUBJECT, "lostPassword.txt", Map.of(
                "user", u, "url", mailer.absoluteUrl("/user/recoverPassword/" + u.getSecurityHash() + ".page")));
        });
        return user;
    }

    /** Legacy validateLostPasswordHash + saveNewPassword. The hash is single-use. */
    public boolean recoverPassword(String hash, String email, String newPassword) {
        if (hash == null || hash.isBlank() || email == null || newPassword == null || newPassword.isEmpty()) {
            return false;
        }
        Optional<User> user = users.findBySecurityHashAndEmailIgnoreCase(hash.trim(), email.trim());
        user.ifPresent(u -> {
            u.setPassword(passwordEncoder.encode(newPassword));
            u.setSecurityHash(null);
            users.save(u);
        });
        return user.isPresent();
    }

    /**
     * Legacy UserCommon.saveUser: applies the profile form. E-mail and password change need the current
     * password unless the editor is an administrator. Returns the list of warnings (empty = saved).
     */
    public List<String> saveProfile(User u, ProfileForm f, boolean isAdmin) {
        List<String> errors = new ArrayList<>();
        SafeHtml safe = formatter.safeHtml();
        if (isAdmin) {
            if (f.getUsername() != null && !f.getUsername().isBlank()) {
                u.setUsername(f.getUsername().trim());
            }
            if (f.getRank_special() != null) {
                u.setRankId(f.getRank_special());
            }
        }
        u.setIcq(safe.makeSafe(f.getIcq()));
        u.setAim(safe.makeSafe(f.getAim()));
        u.setMsnm(safe.makeSafe(f.getMsn()));
        u.setYim(safe.makeSafe(f.getYim()));
        u.setFrom(safe.makeSafe(f.getLocation()));
        u.setOccupation(safe.makeSafe(f.getOccupation()));
        u.setInterests(safe.makeSafe(f.getInterests()));
        u.setBiography(safe.makeSafe(f.getBiography()));
        u.setSignature(safe.makeSafe(f.getSignature()));
        u.setGender(f.getGender());
        u.setViewEmail("1".equals(f.getViewemail()));
        u.setViewOnline(!"1".equals(f.getHideonline()));
        u.setNotifyPrivateMessages("1".equals(f.getNotifypm()));
        u.setNotifyOnMessagesEnabled("1".equals(f.getNotifyreply()));
        u.setAttachSignature("1".equals(f.getAttachsig()));
        u.setHtmlEnabled("1".equals(f.getAllowhtml()));
        u.setBbCodeEnabled("1".equals(f.getAllowbbcode()));
        u.setSmiliesEnabled("1".equals(f.getAllowsmilies()));
        u.setNotifyAlways("1".equals(f.getNotify_always()));
        u.setNotifyText("1".equals(f.getNotify_text()));
        u.setLang(f.getLanguage() == null || f.getLanguage().isBlank() ? null : f.getLanguage());

        String website = safe.makeSafe(f.getWebsite());
        if (website != null && !website.isBlank() && !website.toLowerCase().startsWith("http://")
            && !website.toLowerCase().startsWith("https://")) {
            website = "http://" + website;
        }
        u.setWebsite(website);

        String current = f.getCurrent_password();
        boolean currentEmpty = current == null || current.isBlank();
        if (isAdmin || !currentEmpty) {
            if (isAdmin || (u.getPassword() != null && passwordEncoder.matches(current, u.getPassword()))) {
                String email = f.getEmail() == null ? null : f.getEmail().trim();
                if (email != null && !email.isEmpty() && !email.equalsIgnoreCase(u.getEmail())
                    && users.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(u.getId())).isPresent()) {
                    errors.add(i18n.getMessage("User.emailExists", email));
                } else if (email != null && !email.isEmpty()) {
                    u.setEmail(safe.makeSafe(email));
                }
                String np = f.getNew_password();
                if (np != null && !np.isEmpty()) {
                    if (f.getPassword_confirm() != null && !np.equals(f.getPassword_confirm())) {
                        errors.add(i18n.getMessage("User.passwordNotMatch"));
                    } else {
                        u.setPassword(passwordEncoder.encode(np));
                    }
                }
            } else {
                errors.add(i18n.getMessage("User.currentPasswordInvalid"));
            }
        }
        return errors;
    }

    public void save(User u) {
        users.save(u);
        permissions.invalidate(u.getId());
    }

    public boolean requiresMailActivation() {
        return config.getBool(ConfigKeys.MAIL_USER_EMAIL_AUTH);
    }

    private String randomHash() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
