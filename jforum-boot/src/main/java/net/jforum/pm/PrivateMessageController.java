package net.jforum.pm;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.domain.PrivateMessage;
import net.jforum.domain.User;
import net.jforum.i18n.I18n;
import net.jforum.repository.UserRepository;
import net.jforum.security.CurrentUser;
import net.jforum.support.Ids;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

/** Legacy module "pm" (net.jforum.view.forum.PrivateMessageAction). Every action requires a logged user. */
@Controller
public class PrivateMessageController {

    private final PrivateMessageService service;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final I18n i18n;

    public PrivateMessageController(PrivateMessageService service, UserRepository users, CurrentUser currentUser, I18n i18n) {
        this.service = service;
        this.users = users;
        this.currentUser = currentUser;
        this.i18n = i18n;
    }

    private static String toLogin(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "redirect:/user/login.page?returnPath=" + UriUtils.encodeQueryParam(path, "UTF-8");
    }

    private String message(Model model, String html) {
        model.addAttribute("messageTitle", i18n.getMessage("ForumBase.privateMessages"));
        model.addAttribute("messageHtml", html);
        return "user_message";
    }

    @GetMapping({"/pm/inbox.page", "/pm/list.page"})
    public String inbox(HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        model.addAttribute("inbox", true);
        model.addAttribute("pmList", service.inbox(currentUser.getId()));
        return "pm_inbox";
    }

    @GetMapping("/pm/sentbox.page")
    public String sentbox(HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        model.addAttribute("sentbox", true);
        model.addAttribute("pmList", service.sentbox(currentUser.getId()));
        return "pm_sentbox";
    }

    @GetMapping("/pm/send.page")
    public String send(HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        return sendForm(model);
    }

    @GetMapping("/pm/sendTo/{userId}.page")
    public String sendTo(@PathVariable String userId, HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        Integer id = Ids.parse(userId);
        if (id != null && id > 0) {
            users.findById(id).ifPresent(r -> {
                model.addAttribute("toUserId", r.getId());
                model.addAttribute("toUsername", r.getUsername());
            });
        }
        return sendForm(model);
    }

    private String sendForm(Model model) {
        model.addAttribute("user", currentUser.get());
        if (!model.containsAttribute("attachSig")) {
            model.addAttribute("attachSig", currentUser.get().isAttachSignature());
        }
        return "pm_send";
    }

    @PostMapping("/pm/sendSave.page")
    public String sendSave(@RequestParam(required = false) String toUserId, @RequestParam(required = false) String toUsername,
                           @RequestParam(required = false) String subject, @RequestParam(required = false) String message,
                           @RequestParam(required = false) String preview,
                           @RequestParam(name = "disable_bbcode", required = false) String disableBbcode,
                           @RequestParam(name = "disable_smilies", required = false) String disableSmilies,
                           @RequestParam(name = "attach_sig", required = false) String attachSig,
                           HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        Optional<User> to = Optional.ofNullable(Ids.parse(toUserId)).flatMap(users::findById);
        if (to.isEmpty() && toUsername != null && !toUsername.isBlank()) {
            to = users.findByUsernameIgnoreCase(toUsername.trim());
        }
        to = to.filter(u -> !u.isDeleted() && u.getId() != net.jforum.security.AnonymousUser.ID);
        boolean bbCode = disableBbcode == null;
        boolean smilies = disableSmilies == null;
        boolean sig = attachSig != null;
        if (to.isEmpty()) {
            return message(model, i18n.getMessage("PrivateMessage.userIdNotFound"));
        }
        boolean isPreview = "1".equals(preview);
        boolean empty = subject == null || subject.isBlank() || message == null || message.isBlank();
        if (isPreview || empty) {
            model.addAttribute("toUserId", to.get().getId());
            model.addAttribute("toUsername", to.get().getUsername());
            model.addAttribute("subject", subject);
            model.addAttribute("text", message);
            model.addAttribute("disableBbcode", !bbCode);
            model.addAttribute("disableSmilies", !smilies);
            model.addAttribute("attachSig", sig);
            if (empty && !isPreview) {
                model.addAttribute("error", i18n.getMessage("PostForm.textEmpty"));
            } else {
                model.addAttribute("preview", true);
                model.addAttribute("postPreview", service.preview(message, bbCode, smilies));
            }
            return sendForm(model);
        }
        service.send(currentUser.get(), to.get(), subject.trim(), message, bbCode, smilies, sig, request.getRemoteAddr());
        return message(model, i18n.getMessage("PrivateMessage.messageSent", request.getContextPath() + "/pm/inbox.page"));
    }

    @GetMapping("/pm/findUser.page")
    public String findUser(@RequestParam(required = false) String username, Model model) {
        boolean showResult = username != null && !username.isBlank();
        if (showResult) {
            model.addAttribute("namesList", users.findByUsernameContainingIgnoreCase(username.trim(),
                PageRequest.of(0, 50, Sort.by("username"))).getContent());
        }
        model.addAttribute("username", username);
        model.addAttribute("showResult", showResult);
        return "pm_finduser";
    }

    private Optional<PrivateMessage> readable(String id) {
        return service.findById(Ids.parse(id)).filter(pm -> PrivateMessageService.canRead(pm, currentUser.getId()));
    }

    @GetMapping({"/pm/read/{id}.page", "/pm/review/{id}.page"})
    public String read(@PathVariable String id, HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        Optional<PrivateMessage> pm = readable(id);
        if (pm.isEmpty()) {
            return message(model, i18n.getMessage("PrivateMessage.readDenied"));
        }
        service.markRead(pm.get(), currentUser.get());
        model.addAttribute("pm", service.view(pm.get()));
        return "pm_read";
    }

    @GetMapping({"/pm/reply/{id}.page", "/pm/quote/{id}.page", "/pm/quote.page"})
    public String reply(@PathVariable(required = false) String id, @RequestParam(name = "id", required = false) String idParam,
                        HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        Optional<PrivateMessage> pm = readable(id != null ? id : idParam);
        if (pm.isEmpty()) {
            return message(model, i18n.getMessage("PrivateMessage.readDenied"));
        }
        PmView view = service.view(pm.get());
        boolean quote = request.getRequestURI().contains("/pm/quote");
        String prefix = i18n.getMessage("PrivateMessage.replyPrefix");
        String subject = view.getSubject() == null ? "" : view.getSubject();
        model.addAttribute("subject", subject.startsWith(prefix) ? subject : prefix + subject);
        boolean mine = Objects.equals(view.getFromUserId(), currentUser.getId());
        model.addAttribute("toUserId", mine ? view.getToUserId() : view.getFromUserId());
        model.addAttribute("toUsername", mine ? view.getToUsername() : view.getFromUsername());
        model.addAttribute("pmReply", true);
        if (quote) {
            model.addAttribute("text", "[quote=" + view.getFromUsername() + "]" + view.getText() + "[/quote]\n");
        }
        return sendForm(model);
    }

    @PostMapping("/pm/delete.page")
    public String delete(@RequestParam(name = "id", required = false) String[] ids, HttpServletRequest request, Model model) {
        if (!currentUser.isLogged()) {
            return toLogin(request);
        }
        if (ids != null && ids.length > 0) {
            List<Integer> list = Arrays.stream(ids).map(Ids::parse).filter(Objects::nonNull).toList();
            service.delete(list, currentUser.get());
        }
        return message(model, i18n.getMessage("PrivateMessage.deleteDone", request.getContextPath() + "/pm/inbox.page"));
    }
}
