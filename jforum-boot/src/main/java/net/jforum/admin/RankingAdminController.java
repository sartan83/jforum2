package net.jforum.admin;

import net.jforum.domain.Ranking;
import net.jforum.repository.RankingRepository;
import net.jforum.support.SequenceService;
import net.jforum.web.NotFoundException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy RankingAction (module adminRankings). */
@Controller
public class RankingAdminController {

    private final RankingRepository rankings;
    private final SequenceService sequences;

    public RankingAdminController(RankingRepository rankings, SequenceService sequences) {
        this.rankings = rankings;
        this.sequences = sequences;
    }

    @GetMapping("/adminRankings/list.page")
    public String list(Model model) {
        model.addAttribute("ranks", rankings.findAllByOrderByMinAsc());
        return "admin/ranking_list";
    }

    @GetMapping("/adminRankings/insert.page")
    public String insert(Model model) {
        model.addAttribute("action", "insertSave");
        return "admin/ranking_form";
    }

    @GetMapping("/adminRankings/edit/{rankingId}.page")
    public String edit(@PathVariable int rankingId, Model model) {
        model.addAttribute("rank", rankings.findById(rankingId).orElseThrow(() -> new NotFoundException("Ranking not found")));
        model.addAttribute("action", "editSave");
        return "admin/ranking_form";
    }

    @PostMapping("/adminRankings/insertSave.page")
    public String insertSave(@RequestParam("rank_title") String title,
                             @RequestParam(value = "rank_min", required = false) String min,
                             @RequestParam(value = "rank_special", required = false) String special) {
        Ranking r = new Ranking();
        r.setId(sequences.next("rankings"));
        fill(r, title, min, special);
        rankings.save(r);
        return "redirect:/adminRankings/list.page";
    }

    @PostMapping("/adminRankings/editSave.page")
    public String editSave(@RequestParam("rank_id") int rankingId, @RequestParam("rank_title") String title,
                           @RequestParam(value = "rank_min", required = false) String min,
                           @RequestParam(value = "rank_special", required = false) String special) {
        Ranking r = rankings.findById(rankingId).orElseThrow(() -> new NotFoundException("Ranking not found"));
        fill(r, title, min, special);
        rankings.save(r);
        return "redirect:/adminRankings/list.page";
    }

    private static void fill(Ranking r, String title, String min, String special) {
        r.setTitle(AdminSupport.trim(title));
        r.setSpecial("1".equals(special));
        r.setMin(r.isSpecial() ? 0 : AdminSupport.parseInt(min, 0));
    }

    @PostMapping("/adminRankings/delete.page")
    public String delete(@RequestParam(value = "rank_id", required = false) String[] ids) {
        AdminSupport.ids(ids).forEach(rankings::deleteById);
        return "redirect:/adminRankings/list.page";
    }
}
