package net.jforum.user;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Karma;
import net.jforum.domain.Post;
import net.jforum.domain.User;
import net.jforum.i18n.I18n;
import net.jforum.repository.KarmaRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.CurrentUser;
import net.jforum.security.SecurityConstants;
import net.jforum.support.Ids;
import net.jforum.support.SequenceService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/** Legacy module "karma" (net.jforum.view.forum.KarmaAction + GenericKarmaDAO). */
@Controller
public class KarmaController {

    private final KarmaRepository karmas;
    private final PostRepository posts;
    private final UserRepository users;
    private final SequenceService sequences;
    private final CurrentUser currentUser;
    private final ForumConfig config;
    private final I18n i18n;

    public KarmaController(KarmaRepository karmas, PostRepository posts, UserRepository users, SequenceService sequences,
                           CurrentUser currentUser, ForumConfig config, I18n i18n) {
        this.karmas = karmas;
        this.posts = posts;
        this.users = users;
        this.sequences = sequences;
        this.currentUser = currentUser;
        this.config = config;
        this.i18n = i18n;
    }

    @RequestMapping(value = {"/karma/insert/{start}/{postId}/{points}.page", "/karma/insert/{postId}/{points}.page"},
        method = {RequestMethod.GET, RequestMethod.POST})
    public String insert(@PathVariable(required = false) String start, @PathVariable String postId, @PathVariable String points,
                         HttpServletRequest request, Model model) {
        if (!currentUser.canAccess(SecurityConstants.PERM_KARMA_ENABLED)) {
            return error(model, "Karma.featureDisabled", null);
        }
        Integer pid = Ids.parse(postId);
        Optional<Post> post = pid == null ? Optional.empty() : posts.findById(pid);
        if (post.isEmpty()) {
            throw new net.jforum.web.NotFoundException("invalidAction");
        }
        Post p = post.get();
        int s = Optional.ofNullable(Ids.parse(start)).orElse(0);
        String topicUrl = request.getContextPath() + (s > 0 ? "/posts/list/" + s + "/" : "/posts/list/") + p.getTopicId() + ".page#" + p.getId();
        int fromUserId = currentUser.getId();
        if (!currentUser.isLogged()) {
            return error(model, "Karma.anonymousIsDenied", topicUrl);
        }
        if (p.getUserId() != null && p.getUserId() == fromUserId) {
            return error(model, "Karma.cannotSelfVote", topicUrl);
        }
        if (karmas.findByPostIdAndFromUserId(p.getId(), fromUserId).isPresent()) {
            return error(model, "Karma.alreadyVoted", topicUrl);
        }
        Integer pts = Ids.parse(points);
        if (pts == null || pts < config.getInt(ConfigKeys.KARMA_MIN_POINTS, 1) || pts > config.getInt(ConfigKeys.KARMA_MAX_POINTS, 5)) {
            return error(model, "Karma.invalidRange", topicUrl);
        }
        Karma k = new Karma();
        k.setId(sequences.next("karma"));
        k.setFromUserId(fromUserId);
        k.setPostUserId(p.getUserId());
        k.setPostId(p.getId());
        k.setTopicId(p.getTopicId());
        k.setPoints(pts);
        k.setRateDate(Instant.now());
        karmas.save(k);

        List<Karma> postVotes = karmas.findByPostId(p.getId());
        p.setKarma((int) Math.round(postVotes.stream().mapToInt(Karma::getPoints).average().orElse(0)));
        posts.save(p);
        if (p.getUserId() != null) {
            users.findById(p.getUserId()).ifPresent(u -> {
                List<Karma> received = karmas.findByPostUserId(u.getId());
                u.setKarmaVotes(received.size());
                u.setKarmaScore(received.stream().mapToInt(Karma::getPoints).average().orElse(0));
                users.save(u);
            });
        }
        return "redirect:" + topicUrl.substring(request.getContextPath().length());
    }

    @GetMapping("/karma/list.page")
    public String list(Model model) {
        model.addAttribute("messageTitle", i18n.getMessage("Karma.karma"));
        model.addAttribute("messageHtml", i18n.getMessage("invalidAction"));
        return "user_message";
    }

    /** Karma received/given per user in a period (legacy karma.searchByPeriod, dates as yyyy-MM-dd). */
    @GetMapping("/karma/searchByPeriod/{firstPeriod}/{lastPeriod}.page")
    public String searchByPeriod(@PathVariable String firstPeriod, @PathVariable String lastPeriod, Model model) {
        try {
            Instant from = LocalDate.parse(firstPeriod).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant to = LocalDate.parse(lastPeriod).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return search(from, to, firstPeriod + " - " + lastPeriod, model);
        } catch (DateTimeParseException e) {
            throw new net.jforum.web.NotFoundException("invalidAction");
        }
    }

    /** Legacy karma.searchByMonth, month as yyyy-MM. */
    @GetMapping("/karma/searchByMonth/{month}.page")
    public String searchByMonth(@PathVariable String month, Model model) {
        try {
            YearMonth ym = YearMonth.parse(month);
            return search(ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC), month, model);
        } catch (DateTimeParseException e) {
            throw new net.jforum.web.NotFoundException("invalidAction");
        }
    }

    private String search(Instant from, Instant to, String label, Model model) {
        Map<Integer, KarmaRow> rows = new HashMap<>();
        for (Karma k : karmas.findByRateDateBetween(from, to)) {
            if (k.getPostUserId() != null) {
                rows.computeIfAbsent(k.getPostUserId(), KarmaRow::new).received(k.getPoints());
            }
            if (k.getFromUserId() != null) {
                rows.computeIfAbsent(k.getFromUserId(), KarmaRow::new).given();
            }
        }
        Map<Integer, User> byId = new HashMap<>();
        users.findAllById(rows.keySet()).forEach(u -> byId.put(u.getId(), u));
        List<KarmaRow> list = new ArrayList<>(rows.values());
        list.forEach(r -> r.username = byId.containsKey(r.userId) ? byId.get(r.userId).getUsername() : "#" + r.userId);
        list.sort(Comparator.comparingInt(KarmaRow::getTotalPoints).reversed());
        model.addAttribute("rows", list);
        model.addAttribute("period", label);
        return "karma_list";
    }

    private String error(Model model, String key, String topicUrl) {
        model.addAttribute("messageTitle", i18n.getMessage("Karma.karma"));
        model.addAttribute("messageHtml", topicUrl == null ? i18n.getMessage(key) : i18n.getMessage(key, topicUrl));
        return "user_message";
    }

    /** One line of the karma report. */
    public static class KarmaRow {
        private final Integer userId;
        private String username;
        private int votesReceived;
        private int totalPoints;
        private int votesGiven;

        KarmaRow(Integer userId) {
            this.userId = userId;
        }

        void received(int points) {
            votesReceived++;
            totalPoints += points;
        }

        void given() {
            votesGiven++;
        }

        public Integer getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public int getVotesReceived() {
            return votesReceived;
        }

        public int getTotalPoints() {
            return totalPoints;
        }

        public int getVotesGiven() {
            return votesGiven;
        }
    }
}
