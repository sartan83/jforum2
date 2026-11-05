package net.jforum.search;

import net.jforum.domain.Forum;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;

/** One search result row (getters for FreeMarker). */
public class SearchHit {

    private final Post post;
    private final Topic topic;
    private final Forum forum;
    private final String html;

    public SearchHit(Post post, Topic topic, Forum forum, String html) {
        this.post = post;
        this.topic = topic;
        this.forum = forum;
        this.html = html;
    }

    public Post getPost() {
        return post;
    }

    public Topic getTopic() {
        return topic;
    }

    public Forum getForum() {
        return forum;
    }

    public String getHtml() {
        return html;
    }
}
