package net.jforum.boot;

import java.util.Date;
import java.util.Iterator;

import net.jforum.entities.Category;
import net.jforum.entities.Forum;
import net.jforum.entities.Group;
import net.jforum.entities.Post;
import net.jforum.entities.Smilie;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.persistence.Stores;
import net.jforum.security.Role;
import net.jforum.security.RoleValue;
import net.jforum.security.RoleValueCollection;
import net.jforum.util.MD5;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates the initial data of a fresh installation (the former <code>*_data_dump.sql</code> run by the
 * installer): the General/Administration groups with their roles, the Anonymous and Admin users,
 * the smilies, a test category/forum and the welcome topic. Runs only when no user exists.
 */
@Component
public class InstallationData
{
	private static final Logger logger = Logger.getLogger(InstallationData.class);

	static final int ANONYMOUS_ID = 1;
	static final int ADMIN_ID = 2;

	private static final String[] GENERAL_ROLES = { "perm_vote", "perm_karma_enabled", "perm_anonymous_post",
		"perm_create_poll", "perm_bookmarks_enabled", "perm_attachments_download",
		"perm_create_sticky_announcement_topics", "perm_moderation_log" };

	private static final String[] ADMIN_ROLES = { "perm_administration", "perm_moderation",
		"perm_moderation_post_remove", "perm_moderation_post_edit", "perm_moderation_topic_move",
		"perm_moderation_topic_lockUnlock", "perm_moderation_approve_messages",
		"perm_create_sticky_announcement_topics", "perm_vote", "perm_create_poll", "perm_karma_enabled",
		"perm_bookmarks_enabled", "perm_attachments_download", "perm_moderation_log", "perm_full_moderation_log" };

	/** roles with a value, given to both groups for the forum/category 1 */
	private static final String[] VALUED_ROLES = { "perm_forum", "perm_anonymous_post", "perm_category",
		"perm_read_only_forums", "perm_html_disabled", "perm_attachments_enabled", "perm_reply_only",
		"perm_reply_without_moderation" };

	private static final String[][] SMILIES = {
		{ ":)", "3b63d1616c5dfcf29f8a7a031aaa7cad.gif" }, { ":-)", "3b63d1616c5dfcf29f8a7a031aaa7cad.gif" },
		{ ":D", "283a16da79f3aa23fe1025c96295f04f.gif" }, { ":-D", "283a16da79f3aa23fe1025c96295f04f.gif" },
		{ ":(", "9d71f0541cff0a302a0309c5079e8dee.gif" }, { ":mrgreen:", "ed515dbff23a0ee3241dcc0a601c9ed6.gif" },
		{ ":-o", "47941865eb7bbc2a777305b46cc059a2.gif" }, { ":shock:", "385970365b8ed7503b4294502a458efa.gif" },
		{ ":?:", "0a4d7238daa496a758252d0a2b1a1384.gif" }, { "8)", "b2eb59423fbf5fa39342041237025880.gif" },
		{ ":lol:", "97ada74b88049a6d50a6ed40898a03d7.gif" }, { ":x", "1069449046bcd664c21db15b1dfedaee.gif" },
		{ ":P", "69934afc394145350659cd7add244ca9.gif" }, { ":-P", "69934afc394145350659cd7add244ca9.gif" },
		{ ":oops:", "499fd50bc713bfcdf2ab5a23c00c2d62.gif" }, { ":cry:", "c30b4198e0907b23b8246bdd52aa1c3c.gif" },
		{ ":evil:", "2e207fad049d4d292f60607f80f05768.gif" }, { ":twisted:", "908627bbe5e9f6a080977db8c365caff.gif" },
		{ ":roll:", "2786c5c8e1a8be796fb2f726cca5a0fe.gif" }, { ":wink:", "8a80c6485cd926be453217d59a84a888.gif" },
		{ ";)", "8a80c6485cd926be453217d59a84a888.gif" }, { ";-)", "8a80c6485cd926be453217d59a84a888.gif" },
		{ ":!:", "9293feeb0183c67ea1ea8c52f0dbaf8c.gif" }, { ":?", "136dd33cba83140c7ce38db096d05aed.gif" },
		{ ":idea:", "8f7fb9dd46fb8ef86f81154a4feaada9.gif" }, { ":arrow:", "d6741711aa045b812616853b5507fd2a.gif" },
		{ ":hunf:", "0320a00cb4bb5629ab9fc2bc1fcc4e9e.gif" }, { ":-(", "9d71f0541cff0a302a0309c5079e8dee.gif" },
		{ ":XD:", "49869fe8223507d7223db3451e5321aa.gif" }, { ":thumbup:", "e8a506dc4ad763aca51bec4ca7dc8560.gif" },
		{ ":thumbdown:", "e78feac27fa924c4d0ad6cf5819f3554.gif" }, { ":|", "1cfd6e2a9a2c0cf8e74b49b35e2e46c7.gif" } };

	private static final String WELCOME_TEXT = "[b][color=blue][size=18]Congratulations :!: [/size][/color][/b]\n"
		+ "You have completed the installation, and JForum is up and running. \n\n"
		+ "To start administering the board, login as [i]Admin / <the password you supplied in the installer>[/i] "
		+ "and access the [b][url=/admBase/login.page]Admin Control Panel[/url][/b] using the link that shows up "
		+ "in the bottom of the page. There you will be able to create Categories, Forums and much more  :D  \n\n"
		+ "For more information and support, please refer to the following pages:\n\n"
		+ ":arrow: Community forum: http://www.jforum.net/community.jsp\n"
		+ ":arrow: Documentation: http://www.jforum.net/doc\n\n"
		+ "Thank you for choosing JForum.\n\n"
		+ "[url=http://www.jforum.net/doc/Team]The JForum Team[/url]\n\n";

	private final String adminPassword;

	public InstallationData(@Value("${jforum.admin.password:admin}") String adminPassword)
	{
		this.adminPassword = adminPassword;
	}

	public void installIfEmpty()
	{
		if (Stores.user().getTotalUsers() > 0) {
			return;
		}

		logger.info("Empty database: creating the initial JForum data");

		int generalGroupId = this.addGroup("General", "General Users");
		int adminGroupId = this.addGroup("Administration", "Admin Users");

		this.addUser(ANONYMOUS_ID, "Anonymous", "nopass", 0, generalGroupId);
		this.addUser(ADMIN_ID, "Admin", MD5.crypt(this.adminPassword), 1, adminGroupId);

		for (int i = 0; i < SMILIES.length; i++) {
			Smilie s = new Smilie();
			s.setCode(SMILIES[i][0]);
			s.setDiskName(SMILIES[i][1]);
			s.setUrl("<img src=\"#CONTEXT#/images/smilies/" + SMILIES[i][1] + "\" />");
			Stores.smilie().addNew(s);
		}

		this.addRoles(generalGroupId, GENERAL_ROLES);
		this.addRoles(adminGroupId, ADMIN_ROLES);
		this.addValuedRoles(generalGroupId, VALUED_ROLES, "1");
		this.addValuedRoles(adminGroupId, VALUED_ROLES, "1");
		this.addValuedRoles(adminGroupId, new String[] { "perm_moderation_forums" }, "1");

		int categoryId = this.addCategory("Category Test", 1);
		int forumId = this.addForum(categoryId, "Test Forum", "This is a test forum", 1);
		createTopic(forumId, ADMIN_ID, "Welcome to JForum", WELCOME_TEXT, new Date());
	}

	int addGroup(String name, String description)
	{
		Group g = new Group();
		g.setName(name);
		g.setDescription(description);
		Stores.group().addNew(g);

		if (g.getId() > 0) {
			return g.getId();
		}

		for (Iterator iter = Stores.group().selectAll().iterator(); iter.hasNext(); ) {
			Group candidate = (Group)iter.next();

			if (name.equals(candidate.getName())) {
				return candidate.getId();
			}
		}

		throw new IllegalStateException("Group " + name + " was not created");
	}

	void addUser(int id, String username, String password, int totalPosts, int groupId)
	{
		User u = new User();
		u.setId(id);
		u.setUsername(username);
		u.setPassword(password);
		u.setEmail(id == ANONYMOUS_ID ? "" : username.toLowerCase() + "@localhost");
		u.setRegistrationDate(new Date());
		u.setTotalPosts(totalPosts);
		u.setActive(1);
		Stores.user().addNewWithId(u);
		Stores.user().addToGroup(id, new int[] { groupId });
	}

	/**
	 * Adds a user with an auto generated id, member of the given group.
	 */
	int addUser(String username, String password, String email, int groupId)
	{
		User u = new User();
		u.setUsername(username);
		u.setPassword(MD5.crypt(password));
		u.setEmail(email);
		u.setRegistrationDate(new Date());
		u.setActive(1);
		int id = Stores.user().addNew(u);
		Stores.user().addToGroup(id, new int[] { groupId });
		return id;
	}

	private void addRoles(int groupId, String[] names)
	{
		for (int i = 0; i < names.length; i++) {
			Role role = new Role();
			role.setName(names[i]);
			Stores.groupsecurity().addRole(groupId, role);
		}
	}

	void addValuedRoles(int groupId, String[] names, String value)
	{
		for (int i = 0; i < names.length; i++) {
			Role role = new Role();
			role.setName(names[i]);

			RoleValueCollection values = new RoleValueCollection();
			values.add(new RoleValue(value));
			Stores.groupsecurity().addRole(groupId, role, values);
		}
	}

	int addCategory(String name, int order)
	{
		Category c = new Category();
		c.setName(name);
		c.setOrder(order);
		return Stores.category().addNew(c);
	}

	int addForum(int categoryId, String name, String description, int order)
	{
		Forum f = new Forum();
		f.setIdCategories(categoryId);
		f.setName(name);
		f.setDescription(description);
		f.setOrder(order);
		return Stores.forum().addNew(f);
	}

	/**
	 * Same persistence steps as <code>PostAction.insertSave()</code> for a new topic.
	 */
	static int createTopic(int forumId, int userId, String title, String text, Date time)
	{
		User author = Stores.user().selectById(userId);

		Topic t = new Topic();
		t.setForumId(forumId);
		t.setTitle(title);
		t.setTime(time);
		t.setType(Topic.TYPE_NORMAL);
		t.setPostedBy(author);
		t.setTotalViews(1);
		int topicId = Stores.topic().addNew(t);
		t.setId(topicId);

		int postId = addPost(t, userId, title, text, time);
		t.setFirstPostId(postId);
		t.setLastPostId(postId);
		t.setLastPostBy(author);
		t.setLastPostDate(time);
		Stores.topic().update(t);

		Stores.forum().incrementTotalTopics(forumId, 1);
		Stores.forum().setLastPost(forumId, postId);
		return topicId;
	}

	/**
	 * Same persistence steps as <code>PostAction.insertSave()</code> for a reply.
	 */
	static int reply(int topicId, int userId, String text, Date time)
	{
		Topic t = Stores.topic().selectById(topicId);
		User author = Stores.user().selectById(userId);

		int postId = addPost(t, userId, "Re: " + t.getTitle(), text, time);
		t.setLastPostId(postId);
		t.setLastPostBy(author);
		t.setLastPostDate(time);
		t.setTotalReplies(t.getTotalReplies() + 1);
		Stores.topic().update(t);
		Stores.topic().incrementTotalReplies(topicId);

		Stores.user().incrementPosts(userId);
		Stores.forum().setLastPost(t.getForumId(), postId);
		return postId;
	}

	private static int addPost(Topic t, int userId, String subject, String text, Date time)
	{
		Post p = new Post();
		p.setTopicId(t.getId());
		p.setForumId(t.getForumId());
		p.setUserId(userId);
		p.setSubject(subject);
		p.setText(text);
		p.setTime(time);
		p.setUserIp("127.0.0.1");
		p.setBbCodeEnabled(true);
		p.setSmiliesEnabled(true);
		p.setSignatureEnabled(true);
		p.setHtmlEnabled(false);
		return Stores.post().addNew(p);
	}
}
