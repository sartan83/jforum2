package net.jforum.search;

import java.util.function.IntFunction;

/**
 * Legacy "start" offset pagination for URLs that {@link net.jforum.support.Pagination} cannot express
 * (query-string search results, /moderation/showActivityLog/{start}.page). Same getters, so it works
 * with {@code <@layout.pagination p/>}.
 */
public final class OffsetPagination {

    private final int start;
    private final int perPage;
    private final long totalRecords;
    private final IntFunction<String> urlForStart;

    public OffsetPagination(int start, int perPage, long totalRecords, IntFunction<String> urlForStart) {
        this.start = start;
        this.perPage = perPage;
        this.totalRecords = totalRecords;
        this.urlForStart = urlForStart;
    }

    public int getStart() {
        return start;
    }

    public int getPerPage() {
        return perPage;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public int getTotalPages() {
        return perPage <= 0 ? 1 : (int) Math.ceil(totalRecords / (double) perPage);
    }

    public int getCurrentPage() {
        return perPage <= 0 ? 1 : start / perPage + 1;
    }

    public String url(int page) {
        return urlForStart.apply((page - 1) * perPage);
    }
}
