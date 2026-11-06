package net.jforum.api.integration.mail.pop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import net.jforum.boot.JForumApplication;
import net.jforum.document.MailIntegrationDocument;
import net.jforum.entities.MailIntegration;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class POPListenerTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private POPJob job;

	private String popEnabled;

	@BeforeEach
	void setUp()
	{
		this.mongo.remove(new Query(), MailIntegrationDocument.class);
		this.popEnabled = SystemGlobals.getValue(ConfigKeys.MAIL_POP3_INTEGRATION_ENABLED);
	}

	@AfterEach
	void tearDown()
	{
		SystemGlobals.setValue(ConfigKeys.MAIL_POP3_INTEGRATION_ENABLED, this.popEnabled);
	}

	@Test
	void checksEveryIntegrationWithTheConnectorAndPostsTheParsedMessages() throws Exception
	{
		Stores.mailintegration().add(integration(1, "one@example.com"));
		Stores.mailintegration().add(integration(2, "two@example.com"));

		POPConnector connector = mock(POPConnector.class);
		when(connector.listMessages()).thenReturn(new Message[] { message("alice@example.com", "[1] Hello") },
			new Message[] { message("bob@example.com", "Hi") });

		List<List<POPMessage>> posted = new ArrayList<>();
		TestListener listener = new TestListener(connector, posted);
		listener.execute();

		ArgumentCaptor<MailIntegration> integrations = ArgumentCaptor.forClass(MailIntegration.class);
		InOrder order = inOrder(connector);
		order.verify(connector).setMailIntegration(integrations.capture());
		order.verify(connector).openConnection();
		order.verify(connector).listMessages();
		order.verify(connector).closeConnection();
		order.verify(connector).setMailIntegration(integrations.capture());
		order.verify(connector).openConnection();
		order.verify(connector).listMessages();
		order.verify(connector).closeConnection();

		assertEquals(List.of("one@example.com", "two@example.com"),
			integrations.getAllValues().stream().map(MailIntegration::getForumEmail).toList());

		assertEquals(2, posted.size());
		assertEquals(1, posted.get(0).size());
		assertEquals(1, posted.get(1).size(), "each mailbox only posts its own messages");
		assertEquals("alice@example.com", posted.get(0).get(0).getSender());
		assertEquals("[1] Hello", posted.get(0).get(0).getSubject());
		assertEquals("bob@example.com", posted.get(1).get(0).getSender());
	}

	@Test
	void connectionIsClosedWhenPostingFails() throws Exception
	{
		Stores.mailintegration().add(integration(1, "one@example.com"));

		POPConnector connector = mock(POPConnector.class);
		when(connector.listMessages()).thenThrow(new IllegalStateException("boom"));

		try {
			new TestListener(connector, new ArrayList<>()).execute();
		}
		catch (IllegalStateException expected) {
			// propagated, as in the legacy listener
		}

		verify(connector).closeConnection();
	}

	@Test
	void scheduledJobRunsOnlyWhenEnabled()
	{
		POPConnector connector = mock(POPConnector.class);
		Stores.mailintegration().add(integration(1, "one@example.com"));
		when(connector.listMessages()).thenReturn(new Message[0]);

		POPJob job = new POPJob() {
			@Override
			protected POPListener newListener()
			{
				return new TestListener(connector, new ArrayList<>());
			}
		};

		SystemGlobals.setValue(ConfigKeys.MAIL_POP3_INTEGRATION_ENABLED, "false");
		job.execute();
		verify(connector, never()).openConnection();

		SystemGlobals.setValue(ConfigKeys.MAIL_POP3_INTEGRATION_ENABLED, "true");
		job.execute();
		verify(connector, times(1)).openConnection();
	}

	@Test
	void jobIsASpringBean()
	{
		SystemGlobals.setValue(ConfigKeys.MAIL_POP3_INTEGRATION_ENABLED, "false");
		this.job.execute();
	}

	private static MailIntegration integration(int forumId, String email)
	{
		MailIntegration mi = new MailIntegration();
		mi.setForumId(forumId);
		mi.setForumEmail(email);
		mi.setPopHost("localhost");
		mi.setPopPort(110);
		mi.setPopUsername("u");
		mi.setPopPassword("p");
		return mi;
	}

	private static Message message(String from, String subject) throws Exception
	{
		MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
		m.setFrom(new InternetAddress(from));
		m.setRecipient(Message.RecipientType.TO, new InternetAddress("forum@example.com"));
		m.setSubject(subject);
		m.setText("body of " + subject);
		m.setSentDate(new java.util.Date());
		m.saveChanges();
		return m;
	}

	private static class TestListener extends POPListener
	{
		private final List<List<POPMessage>> posted;

		TestListener(POPConnector connector, List<List<POPMessage>> posted)
		{
			this.connector = connector;
			this.posted = posted;
		}

		@Override
		protected POPPostAction newPostAction()
		{
			return new POPPostAction() {
				@Override
				public void insertMessages(POPParser parser)
				{
					TestListener.this.posted.add(new ArrayList<>(parser.getMessages()));
				}
			};
		}
	}
}
