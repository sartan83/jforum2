package net.jforum.boot;

import java.util.Date;
import java.util.Iterator;

import net.jforum.entities.Category;
import net.jforum.persistence.Stores;

import org.apache.log4j.Logger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo content for the <code>demo</code> profile: categories, forums, users, topics and replies.
 * Users <code>mario</code>, <code>giulia</code>, <code>luca</code> (password <code>demo</code>).
 * Idempotent: does nothing if the demo categories already exist.
 */
@Component
@Profile("demo")
public class DemoData
{
	private static final Logger logger = Logger.getLogger(DemoData.class);
	private static final String MAIN_CATEGORY = "Java & Spring";

	private final InstallationData installation;

	public DemoData(InstallationData installation)
	{
		this.installation = installation;
	}

	public void seed()
	{
		for (Iterator iter = Stores.category().selectAll().iterator(); iter.hasNext(); ) {
			if (MAIN_CATEGORY.equals(((Category)iter.next()).getName())) {
				return;
			}
		}

		logger.info("Creating the demo data");

		int generalGroupId = 1;
		int mario = this.installation.addUser("mario", "demo", "mario@example.com", generalGroupId);
		int giulia = this.installation.addUser("giulia", "demo", "giulia@example.com", generalGroupId);
		int luca = this.installation.addUser("luca", "demo", "luca@example.com", generalGroupId);

		int java = this.installation.addCategory(MAIN_CATEGORY, 2);
		int community = this.installation.addCategory("Community", 3);

		int springForum = this.installation.addForum(java, "Spring Boot", "Spring Boot 3, Spring Data and friends", 1);
		int mongoForum = this.installation.addForum(java, "MongoDB", "Document modeling, indexes, Atlas", 2);
		int offTopic = this.installation.addForum(community, "Off-topic", "Everything else", 1);

		for (int forumId : new int[] { springForum, mongoForum, offTopic }) {
			for (int groupId : new int[] { 1, 2 }) {
				this.installation.addValuedRoles(groupId, new String[] { "perm_forum", "perm_anonymous_post",
					"perm_read_only_forums", "perm_html_disabled", "perm_attachments_enabled", "perm_reply_only",
					"perm_reply_without_moderation" }, String.valueOf(forumId));
				this.installation.addValuedRoles(groupId, new String[] { "perm_category" }, String.valueOf(
					forumId == offTopic ? community : java));
			}
		}

		long now = System.currentTimeMillis();
		long hour = 3600 * 1000L;

		int t1 = InstallationData.createTopic(springForum, mario, "Migrating JForum to Spring Boot 3",
			"The forum now runs as an executable [b]Spring Boot[/b] jar on Java 21 :D", new Date(now - 30 * hour));
		InstallationData.reply(t1, giulia, "Great! Are the original templates still used?", new Date(now - 29 * hour));
		InstallationData.reply(t1, mario, "Yes, [i]all of them[/i], unchanged.", new Date(now - 28 * hour));

		int t2 = InstallationData.createTopic(springForum, luca, "Configuring application.yml",
			"Every SystemGlobals key lives under [code]jforum:[/code] in application.yml.", new Date(now - 20 * hour));
		InstallationData.reply(t2, mario, "And SPRING_DATA_MONGODB_URI selects the database :)", new Date(now - 19 * hour));

		int t3 = InstallationData.createTopic(mongoForum, giulia, "Indexes for topics and posts",
			"topics.forumId, posts.topicId, posts.time and a unique index on users.username.", new Date(now - 10 * hour));
		InstallationData.reply(t3, luca, "Plus a text index for the search :idea:", new Date(now - 9 * hour));

		InstallationData.createTopic(mongoForum, mario, "Connecting to MongoDB Atlas",
			"Use a [b]mongodb+srv://[/b] URI; backups and replication are handled by Atlas.", new Date(now - 5 * hour));

		int t5 = InstallationData.createTopic(offTopic, luca, "Hello everybody", "Hi all, I'm Luca ;)",
			new Date(now - 2 * hour));
		InstallationData.reply(t5, giulia, "Welcome Luca!", new Date(now - hour));
	}
}
