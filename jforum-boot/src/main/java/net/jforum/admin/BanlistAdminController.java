package net.jforum.admin;

import java.util.regex.Pattern;

import net.jforum.domain.Banlist;
import net.jforum.repository.BanlistRepository;
import net.jforum.repository.UserRepository;
import net.jforum.support.Ids;
import net.jforum.support.SequenceService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy BanlistAction (module adminBanning). */
@Controller
public class BanlistAdminController {

    private static final Pattern IP = Pattern.compile("^[0-9*]{1,3}(\\.[0-9*]{1,3}){3}$|^[0-9a-fA-F:*]+$");

    private final BanlistRepository banlist;
    private final UserRepository users;
    private final SequenceService sequences;

    public BanlistAdminController(BanlistRepository banlist, UserRepository users, SequenceService sequences) {
        this.banlist = banlist;
        this.users = users;
        this.sequences = sequences;
    }

    @GetMapping("/adminBanning/list.page")
    public String list(Model model) {
        model.addAttribute("banlist", banlist.findAll());
        return "admin/banlist_list";
    }

    @GetMapping("/adminBanning/insert.page")
    public String insert(Model model) {
        return "admin/banlist_form";
    }

    @PostMapping("/adminBanning/insertSave.page")
    public String insertSave(@RequestParam("type") String type, @RequestParam("value") String value, Model model) {
        String v = AdminSupport.trim(value);
        Banlist b = new Banlist();
        switch (type) {
            case "email" -> {
                if (!v.contains("@")) {
                    return error(model, "Banlist.Unknown");
                }
                b.setEmail(v);
            }
            case "user" -> {
                Integer userId = Ids.parse(v);
                if (userId == null || users.findById(userId).isEmpty()) {
                    return error(model, "Banlist.invalidUserId");
                }
                b.setUserId(userId);
            }
            case "ip" -> {
                if (!IP.matcher(v).matches()) {
                    return error(model, "Banlist.Unknown");
                }
                b.setIp(v);
            }
            default -> {
                return error(model, "Banlist.Unknown");
            }
        }
        b.setId(sequences.next("banlist"));
        banlist.save(b);
        return "redirect:/adminBanning/list.page";
    }

    private String error(Model model, String key) {
        model.addAttribute("errorKey", key);
        return "admin/banlist_form";
    }

    @PostMapping("/adminBanning/delete.page")
    public String delete(@RequestParam(value = "banlist_id", required = false) String[] ids) {
        AdminSupport.ids(ids).forEach(banlist::deleteById);
        return "redirect:/adminBanning/list.page";
    }
}
