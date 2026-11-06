package net.jforum.api.integration.mail.pop;

import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.apache.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the POP3 mailboxes of the forums (former Quartz POPJobStarter) on
 * <code>jforum.mail.pop3.integration.cron</code> when <code>mail.pop3.integration.enabled</code> is true.
 */
@Component
public class POPJob
{
	private static final Logger logger = Logger.getLogger(POPJob.class);

	@Scheduled(cron = "${jforum.mail.pop3.integration.cron:0 5 * * * *}")
	public void execute()
	{
		if (!SystemGlobals.getBoolValue(ConfigKeys.MAIL_POP3_INTEGRATION_ENABLED)) {
			return;
		}

		try {
			this.newListener().execute();
		}
		catch (Exception e) {
			logger.warn(e.toString(), e);
		}
	}

	protected POPListener newListener()
	{
		return new POPListener();
	}
}
