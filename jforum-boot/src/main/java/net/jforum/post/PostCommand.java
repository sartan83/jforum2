package net.jforum.post;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import net.jforum.domain.Poll;
import net.jforum.domain.PollOption;
import net.jforum.domain.TopicType;
import net.jforum.support.Ids;

/** Values of the legacy post_form.htm, parsed from the request (also used to re-render the form). */
public class PostCommand {

    private Integer forumId;
    private Integer topicId;
    private Integer postId;
    private String subject = "";
    private String message = "";
    private boolean disableHtml;
    private boolean disableBbCode;
    private boolean disableSmilies;
    private boolean attachSig = true;
    private boolean notify;
    private TopicType topicType = TopicType.NORMAL;
    private String pollLabel = "";
    private int pollLength;
    private List<String> pollOptions = new ArrayList<>();

    public static PostCommand from(HttpServletRequest r) {
        PostCommand c = new PostCommand();
        c.forumId = Ids.parse(r.getParameter("forum_id"));
        c.topicId = Ids.parse(r.getParameter("topic_id"));
        c.postId = Ids.parse(r.getParameter("post_id"));
        c.subject = trim(r.getParameter("subject"));
        c.message = r.getParameter("message") == null ? "" : r.getParameter("message");
        c.disableHtml = r.getParameter("disable_html") != null;
        c.disableBbCode = r.getParameter("disable_bbcode") != null;
        c.disableSmilies = r.getParameter("disable_smilies") != null;
        c.attachSig = r.getParameter("attach_sig") != null;
        c.notify = r.getParameter("notify") != null;
        Integer type = Ids.parse(r.getParameter("topic_type"));
        c.topicType = type == null || type < 0 || type >= TopicType.values().length ? TopicType.NORMAL : TopicType.values()[type];
        c.pollLabel = trim(r.getParameter("poll_label"));
        Integer length = Ids.parse(r.getParameter("poll_length"));
        c.pollLength = length == null || length < 0 ? 0 : length;
        Integer count = Ids.parse(r.getParameter("poll_option_count"));
        int max = Math.max(count == null ? 0 : count, 20);
        for (int i = 0; i <= max; i++) {
            String o = r.getParameter("poll_option_" + i);
            if (o != null && !o.isBlank()) {
                c.pollOptions.add(o.trim());
            }
        }
        return c;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public boolean hasPoll() {
        return !pollLabel.isEmpty();
    }

    /** Legacy PollCommon.fillPollFromRequest: null when no poll label was given. */
    public Poll toPoll() {
        if (!hasPoll()) {
            return null;
        }
        Poll p = new Poll();
        p.setLabel(pollLabel);
        p.setLength(pollLength);
        int id = 1;
        for (String text : pollOptions) {
            PollOption o = new PollOption();
            o.setId(id++);
            o.setText(text);
            p.getOptions().add(o);
        }
        return p;
    }

    public Integer getForumId() { return forumId; }
    public void setForumId(Integer forumId) { this.forumId = forumId; }
    public Integer getTopicId() { return topicId; }
    public void setTopicId(Integer topicId) { this.topicId = topicId; }
    public Integer getPostId() { return postId; }
    public void setPostId(Integer postId) { this.postId = postId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject == null ? "" : subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message == null ? "" : message; }
    public boolean isDisableHtml() { return disableHtml; }
    public void setDisableHtml(boolean v) { this.disableHtml = v; }
    public boolean isDisableBbCode() { return disableBbCode; }
    public void setDisableBbCode(boolean v) { this.disableBbCode = v; }
    public boolean isDisableSmilies() { return disableSmilies; }
    public void setDisableSmilies(boolean v) { this.disableSmilies = v; }
    public boolean isAttachSig() { return attachSig; }
    public void setAttachSig(boolean v) { this.attachSig = v; }
    public boolean isNotify() { return notify; }
    public void setNotify(boolean v) { this.notify = v; }
    public TopicType getTopicType() { return topicType; }
    public void setTopicType(TopicType t) { this.topicType = t == null ? TopicType.NORMAL : t; }
    public int getTopicTypeOrdinal() { return topicType.ordinal(); }
    public String getPollLabel() { return pollLabel; }
    public void setPollLabel(String v) { this.pollLabel = v == null ? "" : v; }
    public int getPollLength() { return pollLength; }
    public void setPollLength(int v) { this.pollLength = v; }
    public List<String> getPollOptions() { return pollOptions; }
    public void setPollOptions(List<String> v) { this.pollOptions = v; }
}
