package net.jforum.view.admin;

import java.util.Date;

import net.jforum.persistence.mongo.MongoSearchManager;
import net.jforum.search.SearchFacade;
import net.jforum.util.preferences.TemplateKeys;

/**
 * Search statistics (<code>adminSearchStats</code>), formerly the Lucene index statistics.
 * The MongoDB text index is maintained by the database, so there is nothing to (re)build:
 * the index operations of the page just show the statistics again.
 */
public class SearchStatsAction extends AdminCommand
{
	public void list()
	{
		if (!(SearchFacade.manager() instanceof MongoSearchManager manager)) {
			this.setTemplateName(TemplateKeys.SEARCH_STATS_NOT_ENABLED);
			return;
		}

		this.setTemplateName(TemplateKeys.SEARCH_STATS_LIST);

		boolean indexExists = manager.hasTextIndex();
		long docs = manager.countSearchablePosts();

		this.context.put("isInformationAvailable", true);
		this.context.put("indexExists", indexExists);
		this.context.put("currentlyIndexing", false);
		this.context.put("isLocked", false);
		this.context.put("lastModified", new Date());
		this.context.put("indexLocation", "MongoDB text index on posts (subject, text)");
		this.context.put("totalMessages", docs);
		this.context.put("indexVersion", MongoSearchManager.TEXT_INDEX_NAME);
		this.context.put("numberOfDocs", docs);
	}

	public void createIndexDirectory()
	{
		if (SearchFacade.manager() != null) {
			SearchFacade.manager().init();
		}

		this.list();
	}

	public void reconstructIndexFromScratch()
	{
		this.list();
	}

	public void cancelIndexing()
	{
		this.list();
	}
}
