package net.jforum.persistence;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static locator for the persistence stores, replacing the old JDBC
 * <code>DataAccessDriver</code> factory. Implementations are Spring beans
 * registered at startup by {@link StoreRegistrar}, so the legacy
 * (non Spring managed) action classes keep a static access point.
 */
public final class Stores
{
	public static final List<Class<?>> TYPES = List.of(
		ApiStore.class,
		AttachmentStore.class,
		BanlistStore.class,
		BannerStore.class,
		BookmarkStore.class,
		CategoryStore.class,
		ConfigStore.class,
		ForumStore.class,
		GroupSecurityStore.class,
		GroupStore.class,
		KarmaStore.class,
		MailIntegrationStore.class,
		ModerationLogStore.class,
		ModerationStore.class,
		PollStore.class,
		PostStore.class,
		PrivateMessageStore.class,
		RankingStore.class,
		SmilieStore.class,
		SummaryStore.class,
		TopicStore.class,
		TreeGroupStore.class,
		UserSessionStore.class,
		UserStore.class);

	private static final Map<Class<?>, Object> STORES = new ConcurrentHashMap<>();

	private Stores() {}

	public static <T> void register(Class<T> type, T store)
	{
		STORES.put(type, store);
	}

	public static boolean isComplete()
	{
		return STORES.keySet().containsAll(TYPES);
	}

	public static void clear()
	{
		STORES.clear();
	}

	private static <T> T get(Class<T> type)
	{
		Object store = STORES.get(type);

		if (store == null) {
			throw new IllegalStateException("No implementation registered for " + type.getName());
		}

		return type.cast(store);
	}

	public static ApiStore api()
	{
		return get(ApiStore.class);
	}

	public static AttachmentStore attachment()
	{
		return get(AttachmentStore.class);
	}

	public static BanlistStore banlist()
	{
		return get(BanlistStore.class);
	}

	public static BannerStore banner()
	{
		return get(BannerStore.class);
	}

	public static BookmarkStore bookmark()
	{
		return get(BookmarkStore.class);
	}

	public static CategoryStore category()
	{
		return get(CategoryStore.class);
	}

	public static ConfigStore config()
	{
		return get(ConfigStore.class);
	}

	public static ForumStore forum()
	{
		return get(ForumStore.class);
	}

	public static GroupSecurityStore groupsecurity()
	{
		return get(GroupSecurityStore.class);
	}

	public static GroupStore group()
	{
		return get(GroupStore.class);
	}

	public static KarmaStore karma()
	{
		return get(KarmaStore.class);
	}

	public static MailIntegrationStore mailintegration()
	{
		return get(MailIntegrationStore.class);
	}

	public static ModerationLogStore moderationlog()
	{
		return get(ModerationLogStore.class);
	}

	public static ModerationStore moderation()
	{
		return get(ModerationStore.class);
	}

	public static PollStore poll()
	{
		return get(PollStore.class);
	}

	public static PostStore post()
	{
		return get(PostStore.class);
	}

	public static PrivateMessageStore privatemessage()
	{
		return get(PrivateMessageStore.class);
	}

	public static RankingStore ranking()
	{
		return get(RankingStore.class);
	}

	public static SmilieStore smilie()
	{
		return get(SmilieStore.class);
	}

	public static SummaryStore summary()
	{
		return get(SummaryStore.class);
	}

	public static TopicStore topic()
	{
		return get(TopicStore.class);
	}

	public static TreeGroupStore treegroup()
	{
		return get(TreeGroupStore.class);
	}

	public static UserSessionStore usersession()
	{
		return get(UserSessionStore.class);
	}

	public static UserStore user()
	{
		return get(UserStore.class);
	}
}
