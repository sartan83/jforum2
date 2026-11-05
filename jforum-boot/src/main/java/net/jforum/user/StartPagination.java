package net.jforum.user;

/**
 * Pagination for listings whose first page has no id suffix, e.g. /user/list.page then /user/list/30.page
 * (same template contract as net.jforum.support.Pagination).
 */
public class StartPagination {

    private final int start;
    private final int perPage;
    private final long totalRecords;
    private final String baseUrl;

    public StartPagination(int start, int perPage, long totalRecords, String baseUrl) {
        this.start = start;
        this.perPage = perPage;
        this.totalRecords = totalRecords;
        this.baseUrl = baseUrl;
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
        return perPage <= 0 ? 1 : (int) Math.max(1, (totalRecords + perPage - 1) / perPage);
    }

    public int getCurrentPage() {
        return perPage <= 0 ? 1 : start / perPage + 1;
    }

    public String url(int page) {
        int s = (page - 1) * perPage;
        return s == 0 ? baseUrl + ".page" : baseUrl + "/" + s + ".page";
    }
}
