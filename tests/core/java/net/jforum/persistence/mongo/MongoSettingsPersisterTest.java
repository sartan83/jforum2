package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;

import net.jforum.boot.JForumApplication;
import net.jforum.boot.JForumEnvironment;
import net.jforum.document.SettingDocument;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoSettingsPersisterTest extends MongoTestSupport
{
	private static final String KEY = "topicsPerPage";

	@Autowired
	private MongoSettingsPersister persister;

	@Autowired
	private JForumEnvironment environment;

	@Autowired
	private MongoTemplate mongo;

	private String defaultValue;

	@BeforeEach
	void setUp() throws Exception
	{
		this.mongo.dropCollection(SettingDocument.class);
		this.environment.start();
		this.defaultValue = SystemGlobals.getValue(KEY);
	}

	@AfterEach
	void tearDown() throws Exception
	{
		this.mongo.dropCollection(SettingDocument.class);
		this.environment.start();
	}

	@Test
	void environmentRegistersTheMongoPersister()
	{
		assertSame(this.persister, SystemGlobals.getSettingsPersister());
		assertNotNull(this.defaultValue, "application.yml default");
	}

	@Test
	void savedSettingsSurviveARestartAndWinOverDefaults() throws Exception
	{
		String newValue = String.valueOf(Integer.parseInt(this.defaultValue) + 7);

		SystemGlobals.setValue(KEY, newValue);
		SystemGlobals.saveInstallation();

		SettingDocument doc = this.mongo.findOne(new Query(Criteria.where("_id").is(KEY)), SettingDocument.class);
		assertEquals(newValue, doc.getValue());
		assertNotNull(doc.getUpdatedAt());
		assertFalse(new File(SystemGlobals.getValue(ConfigKeys.INSTALLATION_CONFIG)).exists(),
			"no installation file is written anymore");

		// restart: SystemGlobals is re-initialized from application.yml, then the Mongo overrides are applied
		this.environment.start();
		assertEquals(newValue, SystemGlobals.getValue(KEY));
		assertEquals(Integer.parseInt(newValue), SystemGlobals.getIntValue(KEY));

		// save again: the existing document is replaced, not duplicated
		SystemGlobals.setValue(KEY, this.defaultValue);
		SystemGlobals.saveInstallation();
		assertEquals(this.defaultValue, this.persister.load().getProperty(KEY));
		assertEquals(1, this.mongo.count(new Query(Criteria.where("_id").is(KEY)), SettingDocument.class));
	}

	@Test
	void refreshAppliesChangesMadeByAnotherInstance()
	{
		this.mongo.save(new SettingDocument(KEY, "99", new java.util.Date()));
		this.mongo.save(new SettingDocument("forum.page.title", "Shared title", new java.util.Date()));

		this.persister.refresh();

		assertEquals("99", SystemGlobals.getValue(KEY));
		assertEquals("Shared title", SystemGlobals.getValue("forum.page.title"));
		assertEquals(0, SystemGlobals.mergeInstallation(this.persister.load()), "nothing changed anymore");
	}
}
