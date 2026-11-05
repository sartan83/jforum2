package net.jforum.user;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.User;
import net.jforum.format.PostFormatter;
import net.jforum.i18n.I18n;
import net.jforum.repository.BookmarkRepository;
import net.jforum.repository.GroupRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.CurrentUser;
import net.jforum.security.SecurityConstants;
import net.jforum.sso.SsoSettings;
import net.jforum.support.Pagination;
import net.jforum.web.NotFoundException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Legacy module "user" (net.jforum.view.forum.UserAction): registration, activation, profile, edit,
 * user listing and password recovery. Login/logout are in {@link LoginController} + Spring Security.
 */
@Controller
public class UserController {

    static final String AGREEMENT_ACCEPTED = "jforum.agreementAccepted";
    static final List<String> LANGUAGES = List.of("en_US", "de_DE", "hu_HU", "it_IT", "nb_NO", "pt_BR", "pt_PT", "zh_CN", "zh_TW");

    private final UserService userService;
    private final UserRepository users;
    private final GroupRepository groups;
    private final PostRepository posts;
    private final TopicRepository topics;
    private final BookmarkRepository bookmarks;
    private final CurrentUser currentUser;
    private final ForumConfig config;
    private final I18n i18n;
    private final CaptchaService captcha;
    private final LoginSupport loginSupport;
    private final AvatarService avatars;
    private final RankingService rankings;
    private final PostFormatter formatter;
    private final LocaleResolver localeResolver;
    private final SsoSettings sso;

    public UserController(UserService userService, UserRepository users, GroupRepository groups, PostRepository posts,
                          TopicRepository topics, BookmarkRepository bookmarks, CurrentUser currentUser, ForumConfig config,
                          I18n i18n, CaptchaService captcha, LoginSupport loginSupport, AvatarService avatars,
                          RankingService rankings, PostFormatter formatter, LocaleResolver localeResolver, SsoSettings sso) {
        this.userService = userService;
        this.users = users;
        this.groups = groups;
        this.posts = posts;
        this.topics = topics;
        this.bookmarks = bookmarks;
        this.currentUser = currentUser;
        this.config = config;
        this.i18n = i18n;
        this.captcha = captcha;
        this.loginSupport = loginSupport;
        this.avatars = avatars;
        this.rankings = rankings;
        this.formatter = formatter;
        this.localeResolver = localeResolver;
        this.sso = sso;
    }

    // ---------------------------------------------------------------- registration

    private boolean registrationDisabled() {
        return (!config.getBool(ConfigKeys.REGISTRATION_ENABLED) && !currentUser.isAdministrator()) || sso.isEnabled();
    }

    private String message(Model model, String title, String html) {
        model.addAttribute("messageTitle", title);
        model.addAttribute("messageHtml", html);
        return "user_message";
    }

    @GetMapping("/user/insert.page")
    public String insert(HttpSession session, Model model) {
        if (registrationDisabled()) {
            return message(model, i18n.getMessage("ForumBase.register"), i18n.getMessage("User.registrationDisabled"));
        }
        if (config.getBool("agreement.show") && session.getAttribute(AGREEMENT_ACCEPTED) == null) {
            model.addAttribute("agreementContents", agreementContents());
            return "user_agreement";
        }
        return insertForm(model, null, null, null);
    }

    @GetMapping("/user/acceptAgreement.page")
    public String acceptAgreement(HttpSession session) {
        session.setAttribute(AGREEMENT_ACCEPTED, Boolean.TRUE);
        return "redirect:/user/insert.page";
    }

    private String insertForm(Model model, String username, String email, String error) {
        model.addAttribute("username", username);
        model.addAttribute("email", email);
        model.addAttribute("error", error);
        model.addAttribute("captchaReg", config.getBool(ConfigKeys.CAPTCHA_REGISTRATION));
        model.addAttribute("timestamp", System.currentTimeMillis());
        return "user_new";
    }

    @PostMapping("/user/insertSave.page")
    public String insertSave(@RequestParam(required = false) String username, @RequestParam(required = false) String password,
                             @RequestParam(name = "password_confirm", required = false) String passwordConfirm,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String captchaResponse,
                             HttpServletRequest request, HttpServletResponse response, Model model) {
        if (registrationDisabled()) {
            return message(model, i18n.getMessage("ForumBase.register"), i18n.getMessage("User.registrationDisabled"));
        }
        String error = null;
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            error = i18n.getMessage("UsernamePasswordCannotBeNull");
        }
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim();
        if (error == null && passwordConfirm != null && !password.equals(passwordConfirm)) {
            error = i18n.getMessage("User.passwordNotMatch");
        }
        if (error == null && (email == null || !email.matches("[^@\\s]+@[^@\\s]+"))) {
            error = i18n.getMessage("User.fillEmail");
        }
        if (error == null) {
            error = userService.validateNewUser(username, email);
        }
        if (error == null && userService.isBanned(email, request.getRemoteAddr())) {
            error = i18n.getMessage("User.registrationDisabled");
        }
        if (error == null && config.getBool(ConfigKeys.CAPTCHA_REGISTRATION) && !captcha.validate(request.getSession(), captchaResponse)) {
            error = i18n.getMessage("CaptchaResponseFails");
        }
        if (error != null) {
            return insertForm(model, username, email, error);
        }
        request.getSession().removeAttribute(AGREEMENT_ACCEPTED);

        boolean requiresActivation = userService.requiresMailActivation();
        User u = userService.create(username, password, email, requiresActivation);
        if (requiresActivation) {
            userService.sendActivationMail(u);
            return message(model, i18n.getMessage("ForumBase.register"), i18n.getMessage("User.GoActivateAccountMessage"));
        }
        userService.sendWelcomeMail(u);
        if (currentUser.isAdministrator()) {
            return "redirect:/adminUsers/list.page";
        }
        loginSupport.login(u, request, response);
        return "redirect:/user/registrationComplete.page";
    }

    @GetMapping("/user/registrationComplete.page")
    public String registrationComplete(HttpServletRequest request, Model model) {
        String ctx = request.getContextPath();
        String msg = i18n.getMessage("User.RegistrationCompleteMessage",
            ctx + "/user/edit/" + currentUser.getId() + ".page", ctx + "/forums/list.page");
        return message(model, i18n.getMessage("User.RegistrationCompleteTitle"), msg);
    }

    @RequestMapping({"/user/activateAccount/{hash}/{userId}.page", "/user/activateAccount.page"})
    public String activateAccount(@PathVariable(required = false) String hash, @PathVariable(required = false) String userId,
                                  @RequestParam(name = "hash", required = false) String hashParam,
                                  @RequestParam(name = "user_id", required = false) String userIdParam,
                                  HttpServletRequest request, HttpServletResponse response, Model model) {
        String h = hash != null ? hash : hashParam;
        Integer id = net.jforum.support.Ids.parse(userId != null ? userId : userIdParam);
        Optional<User> user = userService.activate(id, h);
        if (user.isEmpty()) {
            return message(model, i18n.getMessage("ActivateAccount.title"),
                i18n.getMessage("User.invalidActivationKey", request.getContextPath() + "/user/activateManual.page"));
        }
        userService.sendWelcomeMail(user.get());
        loginSupport.login(user.get(), request, response);
        return "redirect:/user/registrationComplete.page";
    }

    @GetMapping("/user/activateManual.page")
    public String activateManual() {
        return "activation";
    }

    // ---------------------------------------------------------------- profile

    @GetMapping("/user/profile/{userId}.page")
    public String profile(@PathVariable String userId, Model model) {
        User u = userService.findById(net.jforum.support.Ids.parse(userId)).orElseThrow(() -> new NotFoundException("User.notFound"));
        int loggedId = currentUser.getId();
        long nbookmarks = bookmarks.findByUserId(u.getId()).stream()
            .filter(b -> b.isPublicVisible() || loggedId == u.getId()).count();
        model.addAttribute("u", u);
        model.addAttribute("rankTitle", rankings.getRankTitle(u));
        model.addAttribute("karmaEnabled", currentUser.canAccess(SecurityConstants.PERM_KARMA_ENABLED));
        model.addAttribute("nbookmarks", nbookmarks);
        model.addAttribute("ntopics", topics.countByUserId(u.getId()));
        model.addAttribute("nposts", posts.countByUserId(u.getId()));
        model.addAttribute("signatureHtml", u.getSignature() == null ? "" : formatter.format(u.getSignature(), u.isHtmlEnabled(), true, true));
        return "user_profile";
    }

    @GetMapping("/user/avatar/{userId}.page")
    public ResponseEntity<?> avatar(@PathVariable String userId) throws IOException {
        Optional<User> u = userService.findById(net.jforum.support.Ids.parse(userId));
        var res = u.flatMap(avatars::uploaded);
        if (res.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String type = res.get().getContentType();
        return ResponseEntity.ok()
            .contentType(type == null ? MediaType.IMAGE_PNG : MediaType.parseMediaType(type))
            .cacheControl(CacheControl.noCache())
            .body(res.get().getInputStream().readAllBytes());
    }

    private boolean canEdit(Integer userId) {
        return currentUser.isLogged() && userId != null && userId == currentUser.getId();
    }

    @GetMapping("/user/edit/{userId}.page")
    public String edit(@PathVariable String userId, Model model) {
        Integer id = net.jforum.support.Ids.parse(userId);
        if (!canEdit(id)) {
            return currentUser.isLogged() ? profile(userId, model) : "redirect:/user/login.page?returnPath=/user/edit/" + id + ".page";
        }
        User u = userService.findById(id).orElseThrow(() -> new NotFoundException("User.notFound"));
        return editForm(u, model, null);
    }

    @GetMapping("/user/editDone/{userId}.page")
    public String editDone(@PathVariable String userId, Model model) {
        model.addAttribute("editDone", true);
        return edit(userId, model);
    }

    private String editForm(User u, Model model, List<String> warns) {
        model.addAttribute("u", u);
        model.addAttribute("warns", warns);
        model.addAttribute("admin", false);
        model.addAttribute("avatarAllowExternalUrl", config.getBool(ConfigKeys.AVATAR_ALLOW_EXTERNAL_URL));
        model.addAttribute("avatarMaxWidth", config.getInt(ConfigKeys.AVATAR_MAX_WIDTH, 150));
        model.addAttribute("avatarMaxHeight", config.getInt(ConfigKeys.AVATAR_MAX_HEIGHT, 150));
        model.addAttribute("avatarMaxKbSize", avatars.maxKbSize());
        model.addAttribute("gallery", avatars.gallery());
        model.addAttribute("languages", LANGUAGES);
        model.addAttribute("rankTitle", rankings.getRankTitle(u));
        return "user_edit";
    }

    @PostMapping({"/user/editSave.page", "/user/editSave/{userIdPath}.page"})
    public String editSave(@PathVariable(required = false) String userIdPath, @RequestParam(name = "user_id", required = false) String userIdParam,
                           @ModelAttribute ProfileForm form, HttpServletRequest request, HttpServletResponse response, Model model) {
        Integer id = net.jforum.support.Ids.parse(userIdPath != null ? userIdPath : userIdParam);
        if (!canEdit(id)) {
            return currentUser.isLogged() ? profile(String.valueOf(id), model) : "redirect:/user/login.page";
        }
        User u = userService.findById(id).orElseThrow(() -> new NotFoundException("User.notFound"));
        List<String> warns = new ArrayList<>(userService.saveProfile(u, form, false));
        String avatarError = null;
        if (form.getAvatardel() != null) {
            avatars.remove(u);
        } else if (form.getAvatar() != null && !form.getAvatar().isEmpty()) {
            avatarError = avatars.upload(u, form.getAvatar());
        } else if (form.getAvatarGallery() != null && !form.getAvatarGallery().isBlank()) {
            avatarError = avatars.gallery(u, form.getAvatarGallery());
        } else if (config.getBool(ConfigKeys.AVATAR_ALLOW_EXTERNAL_URL) && form.getAvatarUrl() != null && !form.getAvatarUrl().isBlank()
            && !(u.isExternalAvatar() && form.getAvatarUrl().trim().equals(u.getAvatar()))) {
            avatarError = avatars.external(u, form.getAvatarUrl());
        }
        if (avatarError != null) {
            warns.add(i18n.getMessage(avatarError));
        }
        if (!warns.isEmpty()) {
            User fresh = userService.findById(id).orElse(u);
            return editForm(fresh, model, warns);
        }
        userService.save(u);
        if (u.getLang() != null && LANGUAGES.contains(u.getLang())) {
            String[] p = u.getLang().split("_");
            localeResolver.setLocale(request, response, Locale.of(p[0], p[1]));
        }
        currentUser.refresh();
        return "redirect:/user/editDone/" + id + ".page";
    }

    // ---------------------------------------------------------------- listing

    private int usersPerPage() {
        return config.getInt(ConfigKeys.USERS_PER_PAGE, 30);
    }

    @GetMapping({"/user/list.page", "/user/list/{start}.page"})
    public String list(@PathVariable(required = false) Integer start, Model model) {
        int perPage = usersPerPage();
        int s = Pagination.normalizeStart(start, perPage);
        Page<User> page = users.findAll(PageRequest.of(s / perPage, perPage, Sort.by("id")));
        model.addAttribute("users", page.getContent());
        model.addAttribute("pagination", new StartPagination(s, perPage, page.getTotalElements(), "/user/list"));
        model.addAttribute("rank", rankings);
        model.addAttribute("groups", groups.findAll(Sort.by("name")));
        return "user_list";
    }

    @GetMapping({"/user/listGroup/{groupId}.page", "/user/listGroup/{start}/{groupId}.page"})
    public String listGroup(@PathVariable(required = false) Integer start, @PathVariable Integer groupId, Model model) {
        int perPage = usersPerPage();
        int s = Pagination.normalizeStart(start, perPage);
        Page<User> page = users.findByGroupIdsContaining(groupId, PageRequest.of(s / perPage, perPage, Sort.by("id")));
        model.addAttribute("users", page.getContent());
        model.addAttribute("pagination", new Pagination(s, perPage, page.getTotalElements(), "/user/listGroup", String.valueOf(groupId)));
        model.addAttribute("groups", groups.findAll(Sort.by("name")));
        model.addAttribute("groupId", groupId);
        model.addAttribute("rank", rankings);
        return "user_list";
    }

    // ---------------------------------------------------------------- password recovery

    @GetMapping("/user/lostPassword.page")
    public String lostPassword() {
        return "lost_password";
    }

    @PostMapping("/user/lostPasswordSend.page")
    public String lostPasswordSend(@RequestParam(required = false) String username, @RequestParam(required = false) String email,
                                   HttpServletRequest request, Model model) {
        if (userService.prepareLostPassword(username, email).isEmpty()) {
            model.addAttribute("message", i18n.getMessage("PasswordRecovery.invalidUserEmail"));
            return "lost_password";
        }
        return message(model, i18n.getMessage("PasswordRecovery.title"),
            i18n.getMessage("PasswordRecovery.emailSent", request.getContextPath() + "/user/login.page"));
    }

    @GetMapping("/user/recoverPassword/{hash}.page")
    public String recoverPassword(@PathVariable String hash, Model model) {
        model.addAttribute("recoverHash", hash);
        return "recover_password";
    }

    @PostMapping("/user/recoverPasswordValidate.page")
    public String recoverPasswordValidate(@RequestParam(required = false) String recoverHash, @RequestParam(required = false) String email,
                                          @RequestParam(required = false) String newPassword,
                                          @RequestParam(required = false) String password_confirm,
                                          HttpServletRequest request, Model model) {
        boolean ok = (password_confirm == null || password_confirm.equals(newPassword))
            && userService.recoverPassword(recoverHash, email, newPassword);
        String msg = ok ? i18n.getMessage("PasswordRecovery.ok", request.getContextPath() + "/user/login.page")
            : i18n.getMessage("PasswordRecovery.invalidData");
        return message(model, i18n.getMessage("PasswordRecovery.title"), msg);
    }

    // ---------------------------------------------------------------- helpers

    /** Legacy agreementContents(): templates/agreement/terms_&lt;lang&gt;.txt, falling back to agreement.default.file. */
    private String agreementContents() {
        String lang = config.get(ConfigKeys.I18N_DEFAULT, "en_US");
        for (String name : List.of("terms_" + lang + ".txt", config.get("agreement.default.file", "terms_en_US.txt"))) {
            ClassPathResource r = new ClassPathResource("agreement/" + name);
            if (r.exists()) {
                try (InputStream in = r.getInputStream()) {
                    return decode(in.readAllBytes());
                } catch (IOException e) {
                    break;
                }
            }
        }
        return i18n.getMessage("User.agreement.noAgreement");
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
