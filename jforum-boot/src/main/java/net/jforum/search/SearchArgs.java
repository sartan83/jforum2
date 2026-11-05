package net.jforum.search;

import java.time.Instant;

/** Legacy SearchArgs: what /search/search.page was asked for. */
public class SearchArgs {

    private String keywords;
    private Integer authorId;
    private Integer forumId;
    private Instant fromDate;
    private Instant toDate;
    private boolean matchAll = true;
    private boolean orderByTime;
    private boolean ascending;

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Integer getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Integer authorId) {
        this.authorId = authorId;
    }

    public Integer getForumId() {
        return forumId;
    }

    public void setForumId(Integer forumId) {
        this.forumId = forumId;
    }

    public Instant getFromDate() {
        return fromDate;
    }

    public void setFromDate(Instant fromDate) {
        this.fromDate = fromDate;
    }

    public Instant getToDate() {
        return toDate;
    }

    public void setToDate(Instant toDate) {
        this.toDate = toDate;
    }

    public boolean isMatchAll() {
        return matchAll;
    }

    public void setMatchAll(boolean matchAll) {
        this.matchAll = matchAll;
    }

    public boolean isOrderByTime() {
        return orderByTime;
    }

    public void setOrderByTime(boolean orderByTime) {
        this.orderByTime = orderByTime;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    public String[] terms() {
        return keywords == null || keywords.isBlank() ? new String[0] : keywords.trim().split("\\s+");
    }
}
