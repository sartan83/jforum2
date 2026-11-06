package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import net.jforum.document.SettingDocument;
import net.jforum.document.repository.SettingDocumentRepository;
import net.jforum.util.preferences.SettingsPersister;
import net.jforum.util.preferences.SystemGlobals;

import org.apache.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Admin panel overrides (former <code>installation.config</code> file) stored in the <code>settings</code> collection.
 * Loaded by <code>JForumEnvironment</code> when SystemGlobals is initialized, saved by
 * <code>SystemGlobals.saveInstallation()</code>, and periodically re-read so that changes made on another
 * instance are picked up.
 */
@Component
public class MongoSettingsPersister implements SettingsPersister
{
	private static final Logger logger = Logger.getLogger(MongoSettingsPersister.class);

	private final SettingDocumentRepository repository;

	public MongoSettingsPersister(SettingDocumentRepository repository)
	{
		this.repository = repository;
	}

	@Override
	public Properties load()
	{
		Properties p = new Properties();

		for (SettingDocument doc : this.repository.findAll()) {
			if (doc.getValue() != null) {
				p.setProperty(doc.getKey(), doc.getValue());
			}
		}

		return p;
	}

	@Override
	public void save(Properties overrides)
	{
		Date now = new Date();
		List<SettingDocument> docs = new ArrayList<>();

		for (String key : overrides.stringPropertyNames()) {
			docs.add(new SettingDocument(key, overrides.getProperty(key), now));
		}

		this.repository.saveAll(docs);
	}

	@Scheduled(fixedDelayString = "${jforum-settings.refresh-ms:60000}",
		initialDelayString = "${jforum-settings.refresh-ms:60000}")
	public void refresh()
	{
		if (SystemGlobals.getSettingsPersister() != this) {
			return;
		}

		int changed = SystemGlobals.mergeInstallation(this.load());

		if (changed > 0) {
			logger.info(changed + " setting(s) changed by another instance were applied");
		}
	}
}
