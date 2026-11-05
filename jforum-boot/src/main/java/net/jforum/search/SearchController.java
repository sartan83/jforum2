package net.jforum.search;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.jforum.config.ForumConfig;
import net.jforum.domain.Topic;
import net.jforum.forum.ForumService;
import net.jforum.repository.UserRepository;
import net.jforum.security.CurrentUser;
import net.jforum.support.Pagination;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Port of legacy SearchAction: filters (form), search (results) and newMessages. */
@Controller
public class SearchController {

    private final SearchService service;
    private final ForumService forumService;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final ForumConfig config;

    public SearchController(SearchService service, ForumService forumService, CurrentUser currentUser, UserRepository users,
                            ForumConfig config) {
        this.service = service;
        this.forumService = forumService;
        this.currentUser = currentUser;
        this.users = users;
        this.config = config;
    }

    @GetMapping({"/search/filters.page", "/search/filters", "/search/list.page"})
    public String filters(Model model) {
        model.addAttribute("categories", forumService.visibleCategories(currentUser.permissions()));
        return "search";
    }

    @GetMapping({"/search/search.page", "/search/search"})
    public String search(@RequestParam Map<String, String> params, Model model) {
        SearchArgs args = new SearchArgs();
        args.setKeywords(params.get("search_keywords"));
        args.setAuthorId(resolveAuthor(params.get("search_author")));
        args.setForumId(parseInt(params.getOrDefault("search_forum", params.get("forum"))));
        args.setMatchAll(!"any".equalsIgnoreCase(params.get("match_type")));
        args.setOrderByTime("time".equalsIgnoreCase(params.get("sort_by")));
        args.setAscending("ASC".equalsIgnoreCase(params.get("sort_dir")));
        args.setFromDate(parseDate(params.get("from_date"), false));
        args.setToDate(parseDate(params.get("to_date"), true));

        int perPage = config.postsPerPage();
        int start = Pagination.normalizeStart(parseInt(params.get("start")), perPage);
        SearchService.Result result = service.search(args, currentUser.permissions(), start, perPage);

        Map<String, String> keep = new LinkedHashMap<>(params);
        keep.remove("start");
        keep.remove("_csrf");
        String query = keep.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isEmpty())
            .map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        model.addAttribute("hits", result.hits());
        model.addAttribute("totalRecords", result.total());
        model.addAttribute("args", params);
        model.addAttribute("pagination", new OffsetPagination(start, perPage, result.total(),
            s -> "/search/search.page?" + query + (s > 0 ? (query.isEmpty() ? "" : "&") + "start=" + s : "")));
        model.addAttribute("categories", forumService.visibleCategories(currentUser.permissions()));
        return "search_result";
    }

    @GetMapping({"/search/newMessages.page", "/search/newMessages"})
    public String newMessages(@RequestParam(name = "start", required = false) Integer start,
                              @RequestParam(name = "from_date", required = false) String fromDate, Model model) {
        Instant since = parseDate(fromDate, false);
        if (since == null) {
            since = currentUser.isLogged() ? currentUser.get().getLastVisit() : null;
        }
        int perPage = config.topicsPerPage();
        int s = Pagination.normalizeStart(start, perPage);
        long[] total = new long[1];
        List<Topic> topics = service.newMessages(currentUser.permissions(), since, s, perPage, total);
        model.addAttribute("topics", topics);
        model.addAttribute("totalRecords", total[0]);
        model.addAttribute("since", since);
        model.addAttribute("pagination", new OffsetPagination(s, perPage, total[0],
            x -> "/search/newMessages.page" + (x > 0 ? "?start=" + x : "")));
        return "search_new_messages";
    }

    private Integer resolveAuthor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Integer id = parseInt(value);
        if (id != null) {
            return id;
        }
        return users.findByUsernameIgnoreCase(value.trim()).map(u -> u.getId()).orElse(-1);
    }

    private static Integer parseInt(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Accepts yyyy-MM-dd (HTML date inputs); the "to" date is inclusive. */
    private static Instant parseDate(String s, boolean endOfDay) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            LocalDate d = LocalDate.parse(s.trim());
            return endOfDay ? d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1) : d.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
