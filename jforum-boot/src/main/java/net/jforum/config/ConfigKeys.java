package net.jforum.config;

/** Keys from legacy SystemGlobals.properties used by the application (see legacy ConfigKeys). */
public final class ConfigKeys {

    public static final String FORUM_NAME = "forum.name";
    public static final String FORUM_PAGE_TITLE = "forum.page.title";
    public static final String FORUM_LINK = "forum.link";
    public static final String PAGE_METATAG_KEYWORDS = "forum.page.metatag.keywords";
    public static final String PAGE_METATAG_DESCRIPTION = "forum.page.metatag.description";
    public static final String I18N_DEFAULT = "i18n.board.default";
    public static final String TOPICS_PER_PAGE = "topicsPerPage";
    public static final String POSTS_PER_PAGE = "postsPerPage";
    public static final String USERS_PER_PAGE = "usersPerPage";
    public static final String HOT_TOPIC_BEGIN = "hot.topic.begin";
    public static final String RECENT_TOPICS = "topic.recent";
    public static final String HOTTEST_TOPICS = "topic.hottest";
    public static final String REGISTRATION_ENABLED = "registration.enabled";
    public static final String CAPTCHA_REGISTRATION = "captcha.registration";
    public static final String CAPTCHA_POSTS = "captcha.posts";
    public static final String MAIL_USER_EMAIL_AUTH = "mail.user.email.auth";
    public static final String MAIL_NOTIFY_ANSWERS = "mail.notify.answers";
    public static final String MAIL_SENDER = "mail.sender";
    public static final String RSS_ENABLED = "rss.enabled";
    public static final String HTML_TAGS_WELCOME = "html.tags.welcome";
    public static final String HTML_ATTRIBUTES_WELCOME = "html.attributes.welcome";
    public static final String HTML_LINKS_ALLOW_PROTOCOLS = "html.links.allow.protocols";
    public static final String HTML_LINKS_ALLOW_RELATIVE = "html.links.allow.relative";
    public static final String AVATAR_MAX_WIDTH = "avatar.maxWidth";
    public static final String AVATAR_MAX_HEIGHT = "avatar.maxHeight";
    public static final String AVATAR_MAX_KB_SIZE = "avatar.maxKbSize";
    public static final String AVATAR_ALLOW_EXTERNAL_URL = "avatar.allow.external.url";
    public static final String AVATAR_ALLOW_UPLOAD = "avatar.allow.upload";
    public static final String ATTACHMENTS_MAX_POST = "attachments.max.post";
    public static final String KARMA_MIN_POINTS = "karma.min.points";
    public static final String KARMA_MAX_POINTS = "karma.max.points";
    public static final String ANONYMOUS_USER_ID = "anonymous.userId";
    public static final String DEFAULT_USER_GROUP = "defaultUserGroup";
    public static final String AUTHENTICATION_TYPE = "authentication.type";
    public static final String SSO_IMPLEMENTATION = "sso.implementation";
    public static final String DATE_TIME_FORMAT = "dateTime.format";

    private ConfigKeys() {
    }
}
