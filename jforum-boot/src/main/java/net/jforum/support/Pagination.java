package net.jforum.support;

/** Legacy JForum pagination: "start" is a record offset, pages are 1-based for display. */
public final class Pagination {

    private final int start;
    private final int perPage;
    private final long totalRecords;
    private final String baseUrl;
    private final String suffix;

    /** @param baseUrl e.g. "/forums/show"; @param suffix trailing id, e.g. "3" -> /forums/show/15/3.page */
    public Pagination(int start, int perPage, long totalRecords, String baseUrl, String suffix) {
        this.start = start;
        this.perPage = perPage;
        this.totalRecords = totalRecords;
        this.baseUrl = baseUrl;
        this.suffix = suffix;
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

    public int pageNumber() {
        return perPage <= 0 ? 0 : start / perPage;
    }

    /** URL for the page with the given 1-based number, e.g. /forums/show/15/3.page */
    public String url(int page) {
        int s = (page - 1) * perPage;
        return s == 0 ? baseUrl + "/" + suffix + ".page" : baseUrl + "/" + s + "/" + suffix + ".page";
    }

    public static int normalizeStart(Integer start, int perPage) {
        if (start == null || start < 0) {
            return 0;
        }
        return perPage > 0 ? start - (start % perPage) : start;
    }
}
