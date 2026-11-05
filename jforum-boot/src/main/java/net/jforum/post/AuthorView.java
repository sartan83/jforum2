package net.jforum.post;

import net.jforum.domain.User;

/** Author box data (legacy post_show.htm user column). */
public class AuthorView {

    private final User user;
    private final String rankTitle;
    private final String rankImage;
    private final String avatarUrl;
    private final String signatureHtml;
    private final boolean anonymous;

    public AuthorView(User user, String rankTitle, String rankImage, String avatarUrl, String signatureHtml, boolean anonymous) {
        this.user = user;
        this.rankTitle = rankTitle;
        this.rankImage = rankImage;
        this.avatarUrl = avatarUrl;
        this.signatureHtml = signatureHtml;
        this.anonymous = anonymous;
    }

    public User getUser() { return user; }
    public String getRankTitle() { return rankTitle; }
    public String getRankImage() { return rankImage; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getSignatureHtml() { return signatureHtml; }
    public boolean isAnonymous() { return anonymous; }
}
