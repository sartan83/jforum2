package net.jforum.post;

import net.jforum.domain.Post;

/** A post ready for post_show: formatted text, author box and per-post permissions. */
public class PostView {

    private final Post post;
    private final String html;
    private final AuthorView author;
    private final boolean canEdit;

    public PostView(Post post, String html, AuthorView author, boolean canEdit) {
        this.post = post;
        this.html = html;
        this.author = author;
        this.canEdit = canEdit;
    }

    public Post getPost() { return post; }
    public String getHtml() { return html; }
    public AuthorView getAuthor() { return author; }
    public boolean isCanEdit() { return canEdit; }
}
