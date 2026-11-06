package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import net.jforum.ConfigLoader;
import net.jforum.api.rest.RESTAuthentication;
import net.jforum.boot.JForumApplication;
import net.jforum.document.ApiDocument;
import net.jforum.document.GroupDocument;
import net.jforum.document.UserDocument;
import net.jforum.entities.Group;
import net.jforum.entities.User;
import net.jforum.persistence.Stores;
import net.jforum.persistence.UserStore;
import net.jforum.sso.SSOUtils;
import net.jforum.support.MongoTestSupport;
import net.jforum.util.MD5;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoUserStoreTest extends MongoTestSupport
{
	@Autowired
	private UserStore store;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void clean()
	{
		this.mongo.remove(new Query(), UserDocument.class);
		this.mongo.remove(new Query(), GroupDocument.class);
		this.mongo.remove(new Query(), ApiDocument.class);
		this.mongo.remove(new Query(), MongoUserStore.PRIVMSGS);
		this.mongo.remove(new Query(), MongoUserStore.KARMA);
		ConfigLoader.createLoginAuthenticator();
	}

	private int newUser(String username, String password, String email)
	{
		User u = new User();
		u.setUsername(username);
		u.setPassword(MD5.crypt(password));
		u.setEmail(email);
		return this.store.addNew(u);
	}

	private void group(int id, String name)
	{
		GroupDocument g = new GroupDocument();
		g.setId(id);
		g.setGroupName(name);
		g.setParentId(0);
		this.mongo.insert(g);
	}

	private static List<String> names(List users)
	{
		return ((List<User>)users).stream().map(User::getUsername).collect(Collectors.toList());
	}

	@Test
	void isRegisteredAsStoreBean()
	{
		assertSame(this.store, Stores.user());
		assertTrue(this.store instanceof MongoUserStore);
	}

	@Test
	void addNewAllocatesIdAppliesDefaultsAndDefaultGroup()
	{
		this.group(SystemGlobals.getIntValue(ConfigKeys.DEFAULT_USER_GROUP), "General");

		int id = this.newUser("Alice", "secret", "alice@example.com");
		int id2 = this.newUser("Bob", "secret", "bob@example.com");
		assertEquals(id + 1, id2);

		User u = this.store.selectById(id);
		assertEquals(id, u.getId());
		assertEquals("Alice", u.getUsername());
		assertEquals(MD5.crypt("secret"), u.getPassword());
		assertEquals("alice@example.com", u.getEmail());
		assertEquals(0, u.getTotalPosts());
		assertFalse(u.isDeleted());
		assertFalse(u.isActive());
		assertNull(u.getActivationKey());
		assertTrue(u.isPrivateMessagesEnabled());
		assertTrue(u.isBbCodeEnabled());
		assertFalse(u.isHtmlEnabled());
		assertNotNull(u.getRegistrationDate());
		assertEquals(1, u.getGroupsList().size());
		assertEquals("General", ((Group)u.getGroupsList().get(0)).getName());

		assertEquals(0, this.store.selectById(9999).getId());
	}

	@Test
	void addNewWithIdKeepsLegacyId()
	{
		User u = new User();
		u.setId(500);
		u.setUsername("legacy");
		u.setPassword("x");
		u.setEmail("legacy@example.com");
		this.store.addNewWithId(u);

		assertEquals("legacy", this.store.selectById(500).getUsername());
		assertEquals(500, this.store.selectByName("LEGACY").getId());
	}

	@Test
	void usernameIsUniqueIgnoringCase()
	{
		this.newUser("Alice", "a", "a@example.com");
		assertThrows(DuplicateKeyException.class, () -> this.newUser("ALICE", "b", "b@example.com"));
		assertEquals(1, this.store.getTotalUsers());

		assertTrue(this.store.isUsernameRegistered("alice"));
		assertTrue(this.store.isUsernameRegistered("aLiCe"));
		assertFalse(this.store.isUsernameRegistered("alic"));
	}

	@Test
	void selectByNameIsCaseInsensitive()
	{
		int id = this.newUser("Alice", "a", "a@example.com");
		assertEquals(id, this.store.selectByName("aLICE").getId());
		assertNull(this.store.selectByName("nobody"));
	}

	@Test
	void selectByCredentialsMatchesLowerNameAndHash()
	{
		int id = this.newUser("Alice", "secret", "a@example.com");

		assertEquals(id, this.store.selectByCredentials("ALICE", MD5.crypt("secret")).getId());
		assertNull(this.store.selectByCredentials("alice", MD5.crypt("wrong")));
		assertNull(this.store.selectByCredentials("bob", MD5.crypt("secret")));
	}

	@Test
	void validateLoginUsesDefaultLoginAuthenticator()
	{
		int id = this.newUser("Alice", "secret", "a@example.com");

		assertEquals(id, this.store.validateLogin("alice", "secret").getId());
		assertNull(this.store.validateLogin("alice", "wrong"));

		this.store.delete(id);
		assertNull(this.store.validateLogin("alice", "secret"));
		this.store.undelete(id);
		assertNotNull(this.store.validateLogin("alice", "secret"));

		// Pending activation: activation key set and user not active
		User pending = new User();
		pending.setUsername("Pending");
		pending.setPassword(MD5.crypt("pwd"));
		pending.setEmail("p@example.com");
		pending.setActivationKey("ACT");
		int pid = this.store.addNew(pending);
		assertNull(this.store.validateLogin("pending", "pwd"));

		assertFalse(this.store.validateActivationKeyHash(pid, "WRONG"));
		assertTrue(this.store.validateActivationKeyHash(pid, "ACT"));
		this.store.writeUserActive(pid);
		assertTrue(this.store.selectById(pid).isActive());
		assertNull(this.store.selectById(pid).getActivationKey());
		assertEquals(pid, this.store.validateLogin("pending", "pwd").getId());
	}

	@Test
	void pendingActivationsAreOrderedById()
	{
		this.newUser("Active", "a", "a@example.com");

		for (String name : new String[] { "P1", "P2" }) {
			User u = new User();
			u.setUsername(name);
			u.setPassword("x");
			u.setEmail(name + "@example.com");
			u.setActivationKey("key" + name);
			this.store.addNew(u);
		}

		assertEquals(List.of("P1", "P2"), names(this.store.pendingActivations()));
	}

	@Test
	void updatePersistsProfileFields()
	{
		int id = this.newUser("Alice", "a", "a@example.com");

		User u = this.store.selectById(id);
		u.setEmail("new@example.com");
		u.setFrom("Rome");
		u.setWebSite("https://example.com");
		u.setSignature("sig");
		u.setOccupation("dev");
		u.setInterests("chess");
		u.setBiography("bio");
		u.setLang("it_IT");
		u.setHtmlEnabled(true);
		u.setViewEmailEnabled(true);
		u.setNotifyPrivateMessagesEnabled(false);
		u.setPrivateMessagesEnabled(false);
		u.setAvatar("me.png");
		u.setGender("M");
		u.setUsername("Renamed");
		this.store.update(u);

		User r = this.store.selectById(id);
		assertEquals("new@example.com", r.getEmail());
		assertEquals("Rome", r.getFrom());
		assertEquals("https://example.com", r.getWebSite());
		assertEquals("sig", r.getSignature());
		assertEquals("dev", r.getOccupation());
		assertEquals("chess", r.getInterests());
		assertEquals("bio", r.getBiography());
		assertEquals("it_IT", r.getLang());
		assertTrue(r.isHtmlEnabled());
		assertTrue(r.isViewEmailEnabled());
		assertFalse(r.isNotifyPrivateMessagesEnabled());
		assertFalse(r.isPrivateMessagesEnabled());
		assertEquals("me.png", r.getAvatar());
		assertEquals("M", r.getGender());
		assertEquals("Renamed", r.getUsername());
		assertNotNull(this.store.selectByName("renamed"));
	}

	@Test
	void deleteUndeleteAndActivationFlags()
	{
		int id = this.newUser("Alice", "a", "a@example.com");
		assertFalse(this.store.isDeleted(id));

		this.store.delete(id);
		assertTrue(this.store.isDeleted(id));
		assertTrue(this.store.selectById(id).isDeleted());
		assertEquals(1, this.store.getTotalUsers());

		this.store.undelete(id);
		assertFalse(this.store.isDeleted(id));

		this.store.setActive(id, true);
		assertTrue(this.store.selectById(id).isActive());
		this.store.setActive(id, false);
		assertFalse(this.store.selectById(id).isActive());

		this.store.setRanking(id, 7);
		assertEquals(7, this.store.selectById(id).getRankId());
	}

	@Test
	void postCounters()
	{
		int id = this.newUser("Alice", "a", "a@example.com");
		this.store.incrementPosts(id);
		this.store.incrementPosts(id);
		this.store.incrementPosts(id);
		this.store.decrementPosts(id);
		assertEquals(2, this.store.selectById(id).getTotalPosts());
		assertEquals(2, this.mongo.findById(id, UserDocument.class).getUserPosts());
	}

	@Test
	void listingOrderingAndPagination()
	{
		int c = this.newUser("charlie", "x", "c@example.com");
		int a = this.newUser("Alice", "x", "a@example.com");
		int b = this.newUser("bob", "x", "b@example.com");
		int d = this.newUser("Dave", "x", "d@example.com");

		// selectAll(): ORDER BY user_id
		assertEquals(List.of("charlie", "Alice", "bob", "Dave"), names(this.store.selectAll()));

		// selectAll(start, count): ORDER BY username LIMIT start, count
		assertEquals(List.of("Alice", "bob"), names(this.store.selectAll(0, 2)));
		assertEquals(List.of("charlie", "Dave"), names(this.store.selectAll(2, 2)));
		assertEquals(List.of("Dave"), names(this.store.selectAll(3, 10)));

		User listed = (User)this.store.selectAll(0, 1).get(0);
		assertEquals(a, listed.getId());
		assertEquals("a@example.com", listed.getEmail());

		assertEquals(4, this.store.getTotalUsers());
		assertEquals(List.of(c, a, b, d), ((List<User>)this.store.selectAll()).stream().map(User::getId).toList());
	}

	@Test
	void selectAllWithKarmaComputesTotals()
	{
		int a = this.newUser("Alice", "x", "a@example.com");
		int b = this.newUser("Bob", "x", "b@example.com");

		this.mongo.insert(new Document("_id", 1).append("postUserId", a).append("fromUserId", b).append("points", 4),
			MongoUserStore.KARMA);
		this.mongo.insert(new Document("_id", 2).append("postUserId", a).append("fromUserId", b).append("points", 2),
			MongoUserStore.KARMA);

		List<User> all = this.store.selectAllWithKarma();
		assertEquals(2, all.size());
		assertEquals(6, all.get(0).getKarma().getTotalPoints());
		assertEquals(2, all.get(0).getKarma().getVotesReceived());
		assertEquals(3, all.get(0).getKarma().getKarmaPoints());
		assertEquals(0, all.get(0).getKarma().getVotesGiven());
		assertEquals(2, all.get(1).getKarma().getVotesGiven());

		List<User> page = this.store.selectAllWithKarma(1, 1);
		assertEquals(List.of("Bob"), names(page));
	}

	@Test
	void userKarmaWrittenByKarmaStoreIsLoaded()
	{
		int id = this.newUser("karmic", "p", "k@example.com");
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(id)),
			new org.springframework.data.mongodb.core.query.Update().set("userKarma", 3.5), UserDocument.class);

		User u = this.store.selectById(id);
		assertEquals(id, u.getKarma().getId());
		assertEquals(3.5, u.getKarma().getKarmaPoints(), 0.0001);
		assertEquals(0.0, this.store.selectById(this.newUser("plain", "p", "p@example.com")).getKarma().getKarmaPoints(), 0.0001);
	}

	@Test
	void groupMembership()
	{
		this.group(1, "General");
		this.group(2, "Admin");
		this.group(3, "Mods");

		int a = this.newUser("Alice", "x", "a@example.com");
		int b = this.newUser("Bob", "x", "b@example.com");
		int c = this.newUser("Carl", "x", "c@example.com");

		this.store.addToGroup(a, new int[] { 2, 3 });
		this.store.addToGroup(a, new int[] { 2 });
		this.store.addToGroup(c, new int[] { 2 });

		assertEquals(List.of(1, 2, 3), this.mongo.findById(a, UserDocument.class).getGroupIds());
		assertEquals(List.of("General", "Admin", "Mods"),
			((List<Group>)this.store.selectById(a).getGroupsList()).stream().map(Group::getName).toList());

		assertEquals(3, this.store.getTotalUsersByGroup(1));
		assertEquals(2, this.store.getTotalUsersByGroup(2));
		assertEquals(List.of("Alice", "Carl"), names(this.store.selectAllByGroup(2, 0, 10)));
		assertEquals(List.of("Carl"), names(this.store.selectAllByGroup(2, 1, 10)));
		assertEquals(List.of("Alice"), names(this.store.selectAllByGroup(1, 0, 1)));

		this.store.removeFromGroup(a, new int[] { 2, 3 });
		assertEquals(List.of(1), this.mongo.findById(a, UserDocument.class).getGroupIds());
		assertEquals(1, this.store.getTotalUsersByGroup(2));
		assertEquals(3, this.store.getTotalUsersByGroup(1));
		assertEquals(b, ((User)this.store.selectAllByGroup(1, 1, 1).get(0)).getId());
	}

	@Test
	void lastUserRegistered()
	{
		this.newUser("Alice", "x", "a@example.com");
		int b = this.newUser("Bob", "x", "b@example.com");
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(b)),
			new org.springframework.data.mongodb.core.query.Update().set("userRegdate", new Date(0)), UserDocument.class);
		int c = this.newUser("Carl", "x", "c@example.com");

		User last = this.store.getLastUserInfo();
		assertEquals(c, last.getId());
		assertEquals("Carl", last.getUsername());
	}

	@Test
	void findByNameExactAndPartial()
	{
		this.newUser("Alice", "x", "a@example.com");
		this.newUser("Malice", "x", "m@example.com");
		this.newUser("Bob", "x", "b@example.com");
		this.newUser("a.b", "x", "ab@example.com");

		assertEquals(List.of("Alice", "Malice"), names(this.store.findByName("LIC", false)));
		assertEquals(List.of("Alice"), names(this.store.findByName("alice", true)));
		assertTrue(this.store.findByName("lic", true).isEmpty());
		// Regex metacharacters are taken literally
		assertEquals(List.of("a.b"), names(this.store.findByName(".", false)));

		User u = (User)this.store.findByName("bob", true).get(0);
		assertEquals("b@example.com", u.getEmail());
	}

	@Test
	void lostPasswordFlow()
	{
		int id = this.newUser("Alice", "old", "a@example.com");

		assertEquals("Alice", this.store.getUsernameByEmail("a@example.com"));
		assertEquals("", this.store.getUsernameByEmail("nobody@example.com"));
		assertEquals(id, this.store.findByEmail("A@EXAMPLE.com").getId());
		assertNull(this.store.findByEmail("nobody@example.com"));

		this.store.writeLostPasswordHash("a@example.com", "HASH");
		assertFalse(this.store.validateLostPasswordHash("a@example.com", "OTHER"));
		assertTrue(this.store.validateLostPasswordHash("a@example.com", "HASH"));
		// The hash is consumed once validated
		assertFalse(this.store.validateLostPasswordHash("a@example.com", "HASH"));

		this.store.saveNewPassword(MD5.crypt("new"), "a@example.com");
		assertNull(this.store.validateLogin("alice", "old"));
		assertEquals(id, this.store.validateLogin("alice", "new").getId());
	}

	@Test
	void usernameChangeAndAuthHash()
	{
		int id = this.newUser("Alice", "x", "a@example.com");

		assertFalse(this.store.hasUsernameChanged(id, "Alice"));
		assertTrue(this.store.hasUsernameChanged(id, "alice"));

		this.store.updateUsername(id, "Alicia");
		assertEquals("Alicia", this.store.selectById(id).getUsername());
		assertFalse(this.store.isUsernameRegistered("alice"));
		assertTrue(this.store.isUsernameRegistered("ALICIA"));

		assertNull(this.store.getUserAuthHash(id));
		this.store.saveUserAuthHash(id, "auth");
		assertEquals("auth", this.store.getUserAuthHash(id));
	}

	@Test
	void privateMessagesCountUsesNewMessagesOnly()
	{
		int id = this.newUser("Alice", "x", "a@example.com");
		this.mongo.insert(new Document("_id", 1).append("type", 1).append("toUserId", id),
			MongoUserStore.PRIVMSGS);
		this.mongo.insert(new Document("_id", 2).append("type", 1).append("toUserId", id),
			MongoUserStore.PRIVMSGS);
		this.mongo.insert(new Document("_id", 3).append("type", 0).append("toUserId", id),
			MongoUserStore.PRIVMSGS);

		assertEquals(2, this.store.selectById(id).getPrivateMessagesCount());
	}

	@Test
	void ssoUtilsRegistersOnlyUnknownUsers()
	{
		SSOUtils sso = new SSOUtils();
		assertFalse(sso.userExists("LdapUser"));
		sso.register("ldap-pwd", "ldap@example.com");

		User registered = sso.getUser();
		assertTrue(registered.getId() > 0);
		// Legacy UserModel.addNew does not write user_active: no activation key, so the user can log in
		assertNull(this.store.selectById(registered.getId()).getActivationKey());
		assertEquals("ldap@example.com", this.store.selectByName("ldapuser").getEmail());

		SSOUtils again = new SSOUtils();
		assertTrue(again.userExists("ldapuser"));
		again.register("other", "other@example.com");
		assertEquals(registered.getId(), again.getUser().getId());
		assertEquals(1, this.store.getTotalUsers());
	}

	@Test
	void restAuthenticationValidatesApiKey()
	{
		ApiDocument api = new ApiDocument();
		api.setId(1);
		api.setApiKey("KEY-123");
		api.setApiValidity(new Date());
		this.mongo.insert(api);

		RESTAuthentication auth = new RESTAuthentication();
		assertTrue(auth.validateApiKey("KEY-123"));
		assertFalse(auth.validateApiKey("key-123"));
		assertFalse(auth.validateApiKey("missing"));
		assertFalse(auth.validateApiKey(null));
	}
}
