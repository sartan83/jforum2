package net.jforum.summary;

import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.apache.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly posts summary e-mail (former Quartz SummaryJob / SummaryScheduler). Runs on
 * <code>jforum.summary.cron</code> (legacy <code>0 30 12 ? * MON</code>) when <code>summary.enabled</code> is true.
 */
@Component
public class SummaryJob
{
	private static final Logger logger = Logger.getLogger(SummaryJob.class);

	@Scheduled(cron = "${jforum.summary.cron:0 30 12 * * MON}")
	public void execute()
	{
		if (!SystemGlobals.getBoolValue(ConfigKeys.SUMMARY_IS_ENABLED)) {
			return;
		}

		try {
			SummaryModel model = this.newModel();
			model.sendPostsSummary(model.listRecipients());
		}
		catch (Exception e) {
			logger.warn(e.toString(), e);
		}
	}

	protected SummaryModel newModel()
	{
		return new SummaryModel();
	}
}
