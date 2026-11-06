package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.jforum.document.GroupDocument;
import net.jforum.document.UserDocument;
import net.jforum.document.repository.UserDocumentRepository;
import net.jforum.entities.Group;
import net.jforum.entities.KarmaStatus;
import net.jforum.entities.User;
import net.jforum.exceptions.ForumException;
import net.jforum.persistence.UserStore;
import net.jforum.sso.LoginAuthenticator;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link UserStore}, reproducing the former
 * <code>GenericUserDAO</code> / <code>UserModel.*</code> queries.
 */
@Component
public class MongoUserStore implements UserStore
{
	static final String PRIVMSGS = "privmsgs";
	static final String KARMA = "karma";
	private static final int PRIVMSG_TYPE_NEW = 1;

	private final MongoTemplate mongo;
	private final UserDocumentRepository users;
	private final SequenceGenerator sequences;

	public MongoUserStore(MongoTemplate mongo, UserDocumentRepository users, SequenceGenerator sequences)
	{
		this.mongo = mongo;
		this.users = users;
		this.sequences = sequences;
	}

	static String lower(String s)
	{
		return s == null ? null : s.toLowerCase(Locale.ROOT);
	}

	private static Query byId(int userId)
	{
		return new Query(Criteria.where("_id").is(userId));
	}

	private static Criteria byLowerName(String username)
	{
		return Criteria.where("usernameLower").is(lower(username));
	}

	private static Criteria byEmailIgnoreCase(String email)
	{
		return Criteria.where("userEmail").regex("^" + Pattern.quote(email == null ? "" : email) + "$", "i");
	}

	private static int i(Integer value)
	{
		return value == null ? 0 : value;
	}

	private static int flag(boolean b)
	{
		return b ? 1 : 0;
	}

	private void updateFirst(Query query, Update update)
	{
		this.mongo.updateFirst(query, update, UserDocument.class);
	}

	// --- mapping

	User toUser(UserDocument d)
	{
		User u = new User();

		u.setAim(d.getUserAim());
		u.setAvatar(d.getUserAvatar());
		u.setGender(d.getGender());
		u.setRankId(i(d.getRankId()));
		u.setThemeId(i(d.getThemesId()));
		u.setPrivateMessagesEnabled(i(d.getUserAllowPm()) == 1);
		u.setNotifyOnMessagesEnabled(i(d.getUserNotify()) == 1);
		u.setViewOnlineEnabled(i(d.getUserViewonline()) == 1);
		u.setPassword(d.getUserPassword());
		u.setViewEmailEnabled(i(d.getUserViewemail()) == 1);
		u.setAvatarEnabled(i(d.getUserAllowavatar()) == 1);
		u.setBbCodeEnabled(i(d.getUserAllowbbcode()) == 1);
		u.setHtmlEnabled(i(d.getUserAllowhtml()) == 1);
		u.setSmiliesEnabled(i(d.getUserAllowsmilies()) == 1);
		u.setEmail(d.getUserEmail());
		u.setFrom(d.getUserFrom());
		u.setIcq(d.getUserIcq());
		u.setId(d.getId());
		u.setInterests(d.getUserInterests());
		u.setBiography(d.getUserBiography());
		u.setLastVisit(d.getUserLastvisit());
		u.setOccupation(d.getUserOcc());
		u.setTotalPosts(i(d.getUserPosts()));
		u.setRegistrationDate(d.getUserRegdate());
		u.setSignature(d.getUserSig());
		u.setWebSite(d.getUserWebsite());
		u.setYim(d.getUserYim());
		u.setUsername(d.getUsername());
		u.setAttachSignatureEnabled(i(d.getUserAttachsig()) == 1);
		u.setMsnm(d.getUserMsnm());
		u.setLang(d.getUserLang());
		u.setActive(i(d.getUserActive()));
		u.setKarma(new KarmaStatus(u.getId(), d.getUserKarma() == null ? 0 : d.getUserKarma()));
		u.setNotifyPrivateMessagesEnabled(i(d.getUserNotifyPm()) == 1);
		u.setDeleted(i(d.getDeleted()));
		u.setNotifyAlways(i(d.getUserNotifyAlways()) == 1);
		u.setNotifyText(i(d.getUserNotifyText()) == 1);

		String actkey = d.getUserActkey();
		u.setActivationKey(actkey == null || "".equals(actkey) ? null : actkey);

		return u;
	}

	/** Columns of <code>UserModel.selectAll*</code> (processSelectAll). */
	private User toListedUser(UserDocument d)
	{
		User u = new User();

		u.setEmail(d.getUserEmail());
		u.setId(d.getId());
		u.setTotalPosts(i(d.getUserPosts()));
		u.setRegistrationDate(d.getUserRegdate());
		u.setUsername(d.getUsername());
		u.setDeleted(i(d.getDeleted()));
		KarmaStatus karma = new KarmaStatus();
		karma.setKarmaPoints(d.getUserKarma() == null ? 0 : d.getUserKarma());
		u.setKarma(karma);
		u.setFrom(d.getUserFrom());
		u.setWebSite(d.getUserWebsite());
		u.setViewEmailEnabled(i(d.getUserViewemail()) == 1);

		return u;
	}

	private List toListedUsers(List<UserDocument> docs)
	{
		List list = new ArrayList(docs.size());

		for (UserDocument d : docs) {
			list.add(this.toListedUser(d));
		}

		return list;
	}

	/** Column defaults of the legacy <code>jforum_users</code> DDL. */
	private UserDocument newUserDocument(User user, int id)
	{
		UserDocument d = new UserDocument();

		d.setId(id);
		d.setUsername(user.getUsername());
		d.setUsernameLower(lower(user.getUsername()));
		d.setUserPassword(user.getPassword());
		d.setUserEmail(user.getEmail());
		d.setUserRegdate(new Date());
		d.setUserActkey(user.getActivationKey());
		d.setRankId(0);
		d.setUserActive(user.isActive() ? 1 : 0);
		d.setDeleted(0);
		d.setUserSessionTime(0L);
		d.setUserSessionPage(0);
		d.setUserPosts(user.getTotalPosts());
		d.setUserTimezone("");
		d.setUserLang("");
		d.setUserDateformat("%d/%M/%Y %H:%i");
		d.setUserNewPrivmsg(0);
		d.setUserUnreadPrivmsg(0);
		d.setUserViewemail(0);
		d.setUserAttachsig(1);
		d.setUserAllowhtml(0);
		d.setUserAllowbbcode(1);
		d.setUserAllowsmilies(1);
		d.setUserAllowavatar(1);
		d.setUserAllowPm(1);
		d.setUserAllowViewonline(1);
		d.setUserNotify(1);
		d.setUserNotifyAlways(0);
		d.setUserNotifyText(0);
		d.setUserNotifyPm(1);
		d.setUserPopupPm(1);
		d.setUserAvatarType(0);
		d.setUserViewonline(1);
		d.setUserKarma(0d);

		return d;
	}

	// --- UserStore

	public List pendingActivations()
	{
		Query q = new Query(Criteria.where("userActkey").ne(null)).with(Sort.by("_id"));
		q.fields().include("username", "userRegdate");

		List l = new ArrayList();

		for (UserDocument d : this.mongo.find(q, UserDocument.class)) {
			User user = new User();
			user.setId(d.getId());
			user.setUsername(d.getUsername());
			user.setRegistrationDate(d.getUserRegdate());
			l.add(user);
		}

		return l;
	}

	public User selectById(int userId)
	{
		UserDocument d = this.users.findById(userId).orElse(null);

		if (d == null) {
			return new User();
		}

		User u = this.toUser(d);
		u.setPrivateMessagesCount((int)this.mongo.count(new Query(Criteria.where("type").is(PRIVMSG_TYPE_NEW)
			.and("toUserId").is(userId)), PRIVMSGS));

		if (!d.getGroupIds().isEmpty()) {
			Query q = new Query(Criteria.where("_id").in(d.getGroupIds())).with(Sort.by("_id"));

			for (GroupDocument g : this.mongo.find(q, GroupDocument.class)) {
				Group group = new Group();
				group.setName(g.getGroupName());
				group.setId(g.getId());
				u.getGroupsList().add(group);
			}
		}

		return u;
	}

	public User selectByName(String username)
	{
		UserDocument d = this.mongo.findOne(new Query(byLowerName(username)), UserDocument.class);
		return d == null ? null : this.toUser(d);
	}

	public User selectByCredentials(String username, String hashedPassword)
	{
		UserDocument d = this.mongo.findOne(new Query(byLowerName(username).and("userPassword").is(hashedPassword)),
			UserDocument.class);
		return d == null ? null : this.toUser(d);
	}

	public void delete(int userId)
	{
		this.updateFirst(byId(userId), new Update().set("deleted", 1));
	}

	public void undelete(int userId)
	{
		this.updateFirst(byId(userId), new Update().set("deleted", 0));
	}

	public void update(User user)
	{
		if (user.getLastVisit() == null) {
			user.setLastVisit(new Date());
		}

		Update u = new Update()
			.set("userAim", user.getAim())
			.set("userAvatar", user.getAvatar())
			.set("gender", user.getGender())
			.set("themesId", user.getThemeId())
			.set("userAllowPm", flag(user.isPrivateMessagesEnabled()))
			.set("userAllowavatar", flag(user.isAvatarEnabled()))
			.set("userAllowbbcode", flag(user.isBbCodeEnabled()))
			.set("userAllowhtml", flag(user.isHtmlEnabled()))
			.set("userAllowsmilies", flag(user.isSmiliesEnabled()))
			.set("userEmail", user.getEmail())
			.set("userFrom", user.getFrom())
			.set("userIcq", user.getIcq())
			.set("userInterests", user.getInterests())
			.set("userOcc", user.getOccupation())
			.set("userSig", user.getSignature())
			.set("userWebsite", user.getWebSite())
			.set("userYim", user.getYim())
			.set("userMsnm", user.getMsnm())
			.set("userPassword", user.getPassword())
			.set("userViewemail", flag(user.isViewEmailEnabled()))
			.set("userViewonline", flag(user.isViewOnlineEnabled()))
			.set("userNotify", flag(user.isNotifyOnMessagesEnabled()))
			.set("userAttachsig", flag(user.getAttachSignatureEnabled()))
			.set("username", user.getUsername())
			.set("usernameLower", lower(user.getUsername()))
			.set("userLang", user.getLang())
			.set("userNotifyPm", flag(user.isNotifyPrivateMessagesEnabled()))
			.set("userBiography", user.getBiography())
			.set("userLastvisit", user.getLastVisit())
			.set("userNotifyAlways", flag(user.notifyAlways()))
			.set("userNotifyText", flag(user.notifyText()))
			.set("rankId", user.getRankId());

		this.updateFirst(byId(user.getId()), u);
	}

	public int addNew(User user)
	{
		int id = this.sequences.next(UserDocument.COLLECTION);
		this.users.insert(this.newUserDocument(user, id));
		this.addToGroup(id, new int[] { SystemGlobals.getIntValue(ConfigKeys.DEFAULT_USER_GROUP) });

		user.setId(id);
		return id;
	}

	public void addNewWithId(User user)
	{
		this.users.insert(this.newUserDocument(user, user.getId()));
		this.sequences.ensureAtLeast(UserDocument.COLLECTION, user.getId());
		this.addToGroup(user.getId(), new int[] { SystemGlobals.getIntValue(ConfigKeys.DEFAULT_USER_GROUP) });
	}

	public void setActive(int userId, boolean active)
	{
		this.updateFirst(byId(userId), new Update().set("userActive", flag(active)));
	}

	public void setRanking(int userId, int rankingId)
	{
		this.updateFirst(byId(userId), new Update().set("rankId", rankingId));
	}

	public void incrementPosts(int userId)
	{
		this.updateFirst(byId(userId), new Update().inc("userPosts", 1));
	}

	public void decrementPosts(int userId)
	{
		this.updateFirst(byId(userId), new Update().inc("userPosts", -1));
	}

	public List selectAll()
	{
		return this.selectAll(0, 0);
	}

	public List selectAll(int startFrom, int count)
	{
		Query q = new Query();

		if (count > 0) {
			// UserModel.selectAllByLimit: ORDER BY username (case-insensitive collation), LIMIT start, count
			q.with(Sort.by("usernameLower", "_id")).skip(Math.max(startFrom, 0)).limit(count);
		}
		else {
			q.with(Sort.by("_id"));
		}

		return this.toListedUsers(this.mongo.find(q, UserDocument.class));
	}

	public List selectAllWithKarma()
	{
		return this.selectAllWithKarma(0, 0);
	}

	public List selectAllWithKarma(int startFrom, int count)
	{
		List users = this.selectAll(startFrom, count);

		for (Object o : users) {
			this.loadTotalKarma((User)o);
		}

		return users;
	}

	/** Same computation as the former <code>KarmaDAO.getUserTotalKarma</code> on the <code>karma</code> collection. */
	void loadTotalKarma(User user)
	{
		KarmaStatus karma = new KarmaStatus();
		user.setKarma(karma);

		Aggregation agg = Aggregation.newAggregation(
			Aggregation.match(Criteria.where("postUserId").is(user.getId())),
			Aggregation.group().sum("points").as("points").count().as("votes"));

		Document totals = this.mongo.aggregate(agg, KARMA, Document.class).getUniqueMappedResult();

		if (totals != null) {
			karma.setTotalPoints(((Number)totals.get("points")).intValue());
			karma.setVotesReceived(((Number)totals.get("votes")).intValue());
		}

		if (karma.getVotesReceived() != 0) {
			karma.setKarmaPoints(karma.getTotalPoints() / karma.getVotesReceived());
		}

		karma.setVotesGiven((int)this.mongo.count(new Query(Criteria.where("fromUserId").is(user.getId())), KARMA));
	}

	public List selectAllByGroup(int groupId, int start, int count)
	{
		Query q = new Query(Criteria.where("groupIds").is(groupId)).with(Sort.by("_id"))
			.skip(Math.max(start, 0)).limit(count);

		return this.toListedUsers(this.mongo.find(q, UserDocument.class));
	}

	public User getLastUserInfo()
	{
		Query q = new Query().with(Sort.by(Sort.Direction.DESC, "userRegdate").and(Sort.by(Sort.Direction.DESC, "_id")))
			.limit(1);
		q.fields().include("username");

		User u = new User();
		UserDocument d = this.mongo.findOne(q, UserDocument.class);

		if (d != null) {
			u.setUsername(d.getUsername());
			u.setId(d.getId());
		}

		return u;
	}

	public int getTotalUsers()
	{
		return (int)this.users.count();
	}

	public int getTotalUsersByGroup(int groupId)
	{
		return (int)this.mongo.count(new Query(Criteria.where("groupIds").is(groupId)), UserDocument.class);
	}

	public boolean isDeleted(int userId)
	{
		return this.users.findById(userId).map(d -> i(d.getDeleted()) == 1).orElse(false);
	}

	public boolean isUsernameRegistered(String username)
	{
		return this.mongo.exists(new Query(byLowerName(username)), UserDocument.class);
	}

	public User validateLogin(String username, String password)
	{
		LoginAuthenticator authenticator = (LoginAuthenticator)SystemGlobals.getObjectValue(
			ConfigKeys.LOGIN_AUTHENTICATOR_INSTANCE);

		if (authenticator == null) {
			throw new ForumException("There is no login.authenticator configured. Check your login.authenticator configuration key.");
		}

		authenticator.setUserModel(this);

		return authenticator.validateLogin(username, password, null);
	}

	public void addToGroup(int userId, int[] groupId)
	{
		this.updateFirst(byId(userId), new Update().addToSet("groupIds").each(boxed(groupId)));
	}

	public void removeFromGroup(int userId, int[] groupId)
	{
		this.updateFirst(byId(userId), new Update().pullAll("groupIds", boxed(groupId)));
	}

	private static Object[] boxed(int[] ids)
	{
		return Arrays.stream(ids).boxed().toArray();
	}

	public void writeLostPasswordHash(String email, String hash)
	{
		this.mongo.updateMulti(new Query(Criteria.where("userEmail").is(email)), new Update().set("securityHash", hash),
			UserDocument.class);
	}

	public boolean validateLostPasswordHash(String email, String hash)
	{
		boolean valid = this.mongo.exists(new Query(Criteria.where("securityHash").is(hash).and("userEmail").is(email)),
			UserDocument.class);

		if (valid) {
			this.writeLostPasswordHash(email, "");
		}

		return valid;
	}

	public void saveNewPassword(String password, String email)
	{
		this.mongo.updateMulti(new Query(Criteria.where("userEmail").is(email)),
			new Update().set("userPassword", password).set("securityHash", null), UserDocument.class);
	}

	public String getUsernameByEmail(String email)
	{
		Query q = new Query(Criteria.where("userEmail").is(email)).with(Sort.by("_id"));
		UserDocument d = this.mongo.findOne(q, UserDocument.class);

		return d == null ? "" : d.getUsername();
	}

	public List findByName(String input, boolean exactMatch)
	{
		Criteria c = exactMatch
			? byLowerName(input)
			: Criteria.where("usernameLower").regex(Pattern.quote(lower(input)));

		Query q = new Query(c).with(Sort.by("_id"));
		q.fields().include("username", "userEmail", "deleted");

		List namesList = new ArrayList();

		for (UserDocument d : this.mongo.find(q, UserDocument.class)) {
			User u = new User();
			u.setId(d.getId());
			u.setUsername(d.getUsername());
			u.setEmail(d.getUserEmail());
			u.setDeleted(i(d.getDeleted()));
			namesList.add(u);
		}

		return namesList;
	}

	public boolean validateActivationKeyHash(int userId, String hash)
	{
		return this.mongo.count(new Query(Criteria.where("_id").is(userId).and("userActkey").is(hash)),
			UserDocument.class) == 1;
	}

	public void writeUserActive(int userId)
	{
		this.updateFirst(byId(userId), new Update().set("userActive", 1).set("userActkey", null));
	}

	public void updateUsername(int userId, String username)
	{
		this.updateFirst(byId(userId), new Update().set("username", username).set("usernameLower", lower(username)));
	}

	public boolean hasUsernameChanged(int userId, String usernameToCheck)
	{
		String dbUsername = this.users.findById(userId).map(UserDocument::getUsername).orElse(null);
		return !usernameToCheck.equals(dbUsername);
	}

	public void saveUserAuthHash(int userId, String hash)
	{
		this.updateFirst(byId(userId), new Update().set("userAuthhash", hash));
	}

	public String getUserAuthHash(int userId)
	{
		return this.users.findById(userId).map(UserDocument::getUserAuthhash).orElse(null);
	}

	public User findByEmail(String email)
	{
		UserDocument d = this.mongo.findOne(new Query(byEmailIgnoreCase(email)).with(Sort.by("_id")), UserDocument.class);
		return d == null ? null : this.toUser(d);
	}
}
