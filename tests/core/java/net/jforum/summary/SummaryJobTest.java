package net.jforum.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import net.jforum.boot.JForumApplication;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SummaryJobTest extends MongoTestSupport
{
	@Value("${jforum.summary.cron}")
	private String cron;

	private String enabled;

	@BeforeEach
	void setUp()
	{
		this.enabled = SystemGlobals.getValue(ConfigKeys.SUMMARY_IS_ENABLED);
	}

	@AfterEach
	void tearDown()
	{
		SystemGlobals.setValue(ConfigKeys.SUMMARY_IS_ENABLED, this.enabled);
	}

	@Test
	void sendsTheSummaryToTheRecipientsOnlyWhenEnabled()
	{
		List<List> sent = new ArrayList<>();

		SummaryJob job = new SummaryJob() {
			@Override
			protected SummaryModel newModel()
			{
				return new SummaryModel() {
					@Override
					public List listRecipients()
					{
						return List.of("a@example.com", "b@example.com");
					}

					@Override
					public void sendPostsSummary(List recipients)
					{
						sent.add(recipients);
					}
				};
			}
		};

		SystemGlobals.setValue(ConfigKeys.SUMMARY_IS_ENABLED, "false");
		job.execute();
		assertTrue(sent.isEmpty());

		SystemGlobals.setValue(ConfigKeys.SUMMARY_IS_ENABLED, "true");
		job.execute();
		assertEquals(List.of(List.of("a@example.com", "b@example.com")), sent);
	}

	@Test
	void defaultScheduleIsTheLegacyMondayAtHalfPastTwelve() throws Exception
	{
		assertEquals("0 30 12 * * MON", this.cron);

		Method execute = SummaryJob.class.getMethod("execute");
		assertEquals("${jforum.summary.cron:0 30 12 * * MON}", execute.getAnnotation(Scheduled.class).cron());

		java.time.LocalDateTime next = CronExpression.parse(this.cron)
			.next(java.time.LocalDateTime.of(2026, 11, 6, 9, 0));
		assertEquals(java.time.LocalDateTime.of(2026, 11, 9, 12, 30), next);
	}
}
