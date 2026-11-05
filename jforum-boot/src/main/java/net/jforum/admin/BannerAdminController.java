package net.jforum.admin;

import net.jforum.domain.Banner;
import net.jforum.repository.BannerRepository;
import net.jforum.support.SequenceService;
import net.jforum.web.NotFoundException;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy BannerAction (module adminBanner). */
@Controller
public class BannerAdminController {

    private final BannerRepository banners;
    private final SequenceService sequences;

    public BannerAdminController(BannerRepository banners, SequenceService sequences) {
        this.banners = banners;
        this.sequences = sequences;
    }

    @GetMapping("/adminBanner/list.page")
    public String list(Model model) {
        model.addAttribute("banners", banners.findAll(Sort.by("id")));
        return "admin/banner_list";
    }

    @GetMapping({"/adminBanner/insert.page", "/adminBanner/insert/{bannerId}.page"})
    public String insert(Model model) {
        model.addAttribute("action", "insertSave");
        return "admin/banner_form";
    }

    @GetMapping("/adminBanner/edit/{bannerId}.page")
    public String edit(@PathVariable int bannerId, Model model) {
        model.addAttribute("banner", find(bannerId));
        model.addAttribute("action", "editSave");
        return "admin/banner_form";
    }

    @PostMapping("/adminBanner/insertSave.page")
    public String insertSave(@RequestParam java.util.Map<String, String> p) {
        Banner b = new Banner();
        b.setId(sequences.next("banners"));
        fill(b, p);
        banners.save(b);
        return "redirect:/adminBanner/list.page";
    }

    @PostMapping("/adminBanner/editSave.page")
    public String editSave(@RequestParam("banner_id") int bannerId, @RequestParam java.util.Map<String, String> p) {
        Banner b = find(bannerId);
        fill(b, p);
        banners.save(b);
        return "redirect:/adminBanner/list.page";
    }

    @PostMapping({"/adminBanner/delete.page", "/adminBanner/delete/{bannerId}.page"})
    public String delete(@PathVariable(required = false) Integer bannerId,
                         @RequestParam(value = "banner_id", required = false) Integer id) {
        Integer target = bannerId != null ? bannerId : id;
        if (target != null) {
            banners.deleteById(target);
        }
        return "redirect:/adminBanner/list.page";
    }

    private static void fill(Banner b, java.util.Map<String, String> p) {
        b.setName(AdminSupport.trim(p.get("banner_name")));
        b.setActive("1".equals(p.get("banner_active")));
        b.setPlacement(AdminSupport.parseInt(p.get("banner_placement"), 0));
        b.setType(AdminSupport.parseInt(p.get("banner_type"), 0));
        b.setDescription(AdminSupport.trim(p.get("banner_description")));
        b.setUrl(AdminSupport.trim(p.get("banner_url")));
        b.setComment(AdminSupport.trim(p.get("banner_comment")));
        b.setWeight(AdminSupport.parseInt(p.get("banner_weight"), 50));
        b.setWidth(AdminSupport.parseInt(p.get("banner_width"), 0));
        b.setHeight(AdminSupport.parseInt(p.get("banner_height"), 0));
    }

    private Banner find(int id) {
        return banners.findById(id).orElseThrow(() -> new NotFoundException("Banner not found"));
    }
}
