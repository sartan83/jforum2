package net.jforum.admin;

import java.util.List;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.User;
import net.jforum.repository.RankingRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.PermissionService;
import net.jforum.web.NotFoundException;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy admin UserAction (module adminUsers). */
@Controller
public class UserAdminController {

    private final UserRepository users;
    private final RankingRepository rankings;
    private final GroupTree groupTree;
    private final PermissionService permissions;
    private final PasswordEncoder passwordEncoder;
    private final ForumConfig config;
    private final MongoTemplate mongo;

    public UserAdminController(UserRepository users, RankingRepository rankings, GroupTree groupTree,
                               PermissionService permissions, PasswordEncoder passwordEncoder, ForumConfig config,
                               MongoTemplate mongo) {
        this.users = users;
        this.rankings = rankings;
        this.groupTree = groupTree;
        this.permissions = permissions;
        this.passwordEncoder = passwordEncoder;
        this.config = config;
        this.mongo = mongo;
    }

    @GetMapping({"/adminUsers/list.page", "/adminUsers/list/{start}.page", "/adminUsers/search.page"})
    public String list(@PathVariable(required = false) Integer start,
                       @RequestParam(value = "username", required = false) String username,
                       @RequestParam(value = "group_id", required = false) Integer groupId, Model model) {
        int perPage = Math.max(1, config.getInt(ConfigKeys.USERS_PER_PAGE, 30));
        int offset = start == null || start < 0 ? 0 : start - start % perPage;
        Query query = new Query();
        if (username != null && !username.isBlank()) {
            query.addCriteria(Criteria.where("username").regex(java.util.regex.Pattern.quote(username.trim()), "i"));
            model.addAttribute("username", username.trim());
        }
        if (groupId != null && groupId > 0) {
            query.addCriteria(Criteria.where("groupIds").is(groupId));
            model.addAttribute("groupId", groupId);
        }
        long total = mongo.count(query, User.class);
        query.with(Sort.by("id")).skip(offset).limit(perPage);
        model.addAttribute("users", mongo.find(query, User.class));
        model.addAttribute("groups", groupTree.nodes());
        model.addAttribute("start", offset);
        model.addAttribute("perPage", perPage);
        model.addAttribute("total", total);
        return "admin/user_list";
    }

    @GetMapping("/adminUsers/groupSearch/{start}/{groupId}.page")
    public String groupSearch(@PathVariable Integer start, @PathVariable Integer groupId, Model model) {
        return list(start, null, groupId, model);
    }

    @GetMapping({"/adminUsers/edit/{userId}.page", "/adminUsers/edit.page"})
    public String edit(@PathVariable(required = false) Integer userId,
                       @RequestParam(value = "id", required = false) Integer id, Model model) {
        model.addAttribute("u", find(userId != null ? userId : id == null ? -1 : id));
        model.addAttribute("rankings", rankings.findAllByOrderByMinAsc().stream().filter(r -> r.isSpecial()).toList());
        return "admin/user_form";
    }

    @PostMapping("/adminUsers/editSave.page")
    public String editSave(@RequestParam("user_id") int userId,
                           @RequestParam("username") String username,
                           @RequestParam("email") String email,
                           @RequestParam(value = "new_password", required = false) String newPassword,
                           @RequestParam(value = "website", required = false) String website,
                           @RequestParam(value = "location", required = false) String from,
                           @RequestParam(value = "occupation", required = false) String occupation,
                           @RequestParam(value = "interests", required = false) String interests,
                           @RequestParam(value = "signature", required = false) String signature,
                           @RequestParam(value = "biography", required = false) String biography,
                           @RequestParam(value = "rank_special", required = false) String rankId,
                           @RequestParam(value = "viewemail", required = false) String viewEmail,
                           @RequestParam(value = "allowhtml", required = false) String allowHtml,
                           @RequestParam(value = "allowbbcode", required = false) String allowBbcode,
                           @RequestParam(value = "allowsmilies", required = false) String allowSmilies,
                           @RequestParam(value = "attachsig", required = false) String attachSig,
                           @RequestParam(value = "notifyreply", required = false) String notifyReply,
                           @RequestParam(value = "notifypm", required = false) String notifyPm,
                           Model model) {
        User u = find(userId);
        String newUsername = AdminSupport.trim(username);
        if (!newUsername.isEmpty() && !newUsername.equalsIgnoreCase(u.getUsername())
            && users.findByUsernameIgnoreCase(newUsername).isPresent()) {
            model.addAttribute("errorMessage", "User.usernameInvalidChars");
            return edit(userId, null, model);
        }
        if (!newUsername.isEmpty()) {
            u.setUsername(newUsername);
        }
        u.setEmail(AdminSupport.trim(email));
        if (newPassword != null && !newPassword.isBlank()) {
            u.setPassword(passwordEncoder.encode(newPassword));
        }
        u.setWebsite(AdminSupport.trim(website));
        u.setFrom(AdminSupport.trim(from));
        u.setOccupation(AdminSupport.trim(occupation));
        u.setInterests(AdminSupport.trim(interests));
        u.setSignature(AdminSupport.trim(signature));
        u.setBiography(AdminSupport.trim(biography));
        Integer rank = net.jforum.support.Ids.parse(rankId);
        u.setRankId(rank == null || rank == 0 ? null : rank);
        u.setViewEmail("1".equals(viewEmail));
        u.setHtmlEnabled("1".equals(allowHtml));
        u.setBbCodeEnabled("1".equals(allowBbcode));
        u.setSmiliesEnabled("1".equals(allowSmilies));
        u.setAttachSignature("1".equals(attachSig));
        u.setNotifyOnMessagesEnabled("1".equals(notifyReply));
        u.setNotifyPrivateMessages("1".equals(notifyPm));
        users.save(u);
        return "redirect:/adminUsers/list.page";
    }

    /** Legacy UserAction.delete: toggles the "deleted" (locked) flag of the selected users. */
    @PostMapping("/adminUsers/delete.page")
    public String delete(@RequestParam(value = "user_id", required = false) String[] ids) {
        int anonymous = config.getInt(ConfigKeys.ANONYMOUS_USER_ID, 1);
        for (Integer id : AdminSupport.ids(ids)) {
            if (id == anonymous) {
                continue;
            }
            users.findById(id).ifPresent(u -> {
                u.setDeleted(!u.isDeleted());
                users.save(u);
            });
            permissions.invalidate(id);
        }
        return "redirect:/adminUsers/list.page";
    }

    @GetMapping({"/adminUsers/groups/{userId}.page", "/adminUsers/groups.page"})
    public String groups(@PathVariable(required = false) Integer userId,
                         @RequestParam(value = "id", required = false) Integer id, Model model) {
        User u = find(userId != null ? userId : id == null ? -1 : id);
        model.addAttribute("u", u);
        model.addAttribute("groups", groupTree.nodes());
        return "admin/user_groups";
    }

    @PostMapping("/adminUsers/groupsSave.page")
    public String groupsSave(@RequestParam("user_id") int userId,
                             @RequestParam(value = "groups", required = false) String[] groupIds) {
        User u = find(userId);
        List<Integer> selected = AdminSupport.ids(groupIds);
        if (!selected.isEmpty()) {
            u.setGroupIds(new java.util.ArrayList<>(selected));
            users.save(u);
            permissions.invalidate(userId);
        }
        return "redirect:/adminUsers/list.page";
    }

    @GetMapping("/adminUsers/pendingActivations.page")
    public String pendingActivations(Model model) {
        model.addAttribute("users", mongo.find(Query.query(Criteria.where("active").is(false).and("deleted").is(false))
            .with(Sort.by("id")), User.class));
        return "admin/user_pending_activations";
    }

    @PostMapping("/adminUsers/activateAccount.page")
    public String activateAccount(@RequestParam(value = "user_id", required = false) String[] ids) {
        for (Integer id : AdminSupport.ids(ids)) {
            users.findById(id).ifPresent(u -> {
                u.setActive(true);
                u.setActivationKey(null);
                users.save(u);
            });
        }
        return "redirect:/adminUsers/pendingActivations.page";
    }

    private User find(int id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

}
