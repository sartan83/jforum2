package net.jforum.view.forum.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;

import net.jforum.SessionFacade;
import net.jforum.boot.JForumApplication;
import net.jforum.context.RequestContext;
import net.jforum.document.AttachQuotaDocument;
import net.jforum.document.AttachmentDocument;
import net.jforum.document.ExtensionDocument;
import net.jforum.document.ExtensionGroupDocument;
import net.jforum.document.QuotaLimitDocument;
import net.jforum.entities.Attachment;
import net.jforum.entities.AttachmentExtension;
import net.jforum.entities.AttachmentExtensionGroup;
import net.jforum.entities.Group;
import net.jforum.entities.Post;
import net.jforum.entities.QuotaLimit;
import net.jforum.entities.User;
import net.jforum.entities.UserSession;
import net.jforum.exceptions.AttachmentSizeTooBigException;
import net.jforum.exceptions.BadExtensionException;
import net.jforum.persistence.AttachmentStore;
import net.jforum.persistence.Stores;
import net.jforum.persistence.UserStore;
import net.jforum.persistence.mongo.AttachmentFileStorage;
import net.jforum.repository.SecurityRepository;
import net.jforum.support.MongoTestSupport;
import net.jforum.util.I18n;
import net.jforum.util.upload.FileItem;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Upload path of the attachments: extension security, quotas, GridFS storage, thumbnails, removal.
 * The forum permissions, the user session and the user store (owned by other areas) are mocked.
 */
@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AttachmentCommonTest extends MongoTestSupport
{
	private static final int USER_ID = 7;
	private static final int GROUP_ID = 3;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private AttachmentFileStorage storage;

	private AttachmentStore store;
	private RequestContext request;
	private UserStore previousUserStore;
	private MockedStatic<SecurityRepository> security;
	private MockedStatic<SessionFacade> session;
	private MockedStatic<I18n> i18n;

	@BeforeEach
	void setUp()
	{
		for (Class<?> c : List.of(AttachmentDocument.class, ExtensionDocument.class, ExtensionGroupDocument.class,
				QuotaLimitDocument.class, AttachQuotaDocument.class)) {
			this.mongo.remove(new Query(), c);
		}

		this.mongo.remove(new Query(), "posts");
		this.mongo.insert(new Document("_id", 100).append("attach", 0), "posts");

		this.store = Stores.attachment();

		AttachmentExtensionGroup images = new AttachmentExtensionGroup();
		images.setName("Images");
		images.setAllow(true);
		images.setDownloadMode(1);
		this.store.addExtensionGroup(images);

		for (String[] e : new String[][] { { "png", "1" }, { "txt", "1" }, { "exe", "0" } }) {
			AttachmentExtension ext = new AttachmentExtension();
			ext.setExtensionGroupId(images.getId());
			ext.setExtension(e[0]);
			ext.setAllow("1".equals(e[1]));
			this.store.addExtension(ext);
		}

		QuotaLimit quota = new QuotaLimit();
		quota.setDescription("1 KB");
		quota.setSize(1);
		quota.setType(QuotaLimit.KB);
		this.store.addQuotaLimit(quota);
		this.store.setGroupQuota(GROUP_ID, quota.getId());

		User user = new User();
		user.setId(USER_ID);
		user.getGroupsList().add(new Group(GROUP_ID, 0, "Users", ""));
		UserStore users = mock(UserStore.class);
		when(users.selectById(USER_ID)).thenReturn(user);

		try {
			this.previousUserStore = Stores.user();
		}
		catch (IllegalStateException e) {
			this.previousUserStore = null;
		}

		Stores.register(UserStore.class, users);

		UserSession us = new UserSession();
		us.setUserId(USER_ID);

		this.security = mockStatic(SecurityRepository.class);
		this.security.when(() -> SecurityRepository.canAccess(anyString(), anyString())).thenReturn(true);
		this.security.when(() -> SecurityRepository.canAccess(anyString())).thenReturn(true);

		this.session = mockStatic(SessionFacade.class);
		this.session.when(SessionFacade::getUserSession).thenReturn(us);

		this.i18n = mockStatic(I18n.class);
		this.i18n.when(() -> I18n.getMessage(anyString(), org.mockito.ArgumentMatchers.<Object[]>any()))
			.thenAnswer(inv -> inv.getArgument(0));

		this.request = mock(RequestContext.class);
	}

	@AfterEach
	void tearDown()
	{
		this.security.close();
		this.session.close();
		this.i18n.close();

		if (this.previousUserStore != null) {
			Stores.register(UserStore.class, this.previousUserStore);
		}
	}

	@Test
	void uploadsStoreTheFileAndAThumbnailInGridFs() throws IOException
	{
		byte[] png = png(600, 300);
		byte[] txt = "hello".getBytes(StandardCharsets.UTF_8);
		this.files(new TestFileItem("file_0", "C:\\photos\\cat.png", "image/png", png),
			new TestFileItem("file_1", "notes.txt", "text/plain", txt));

		AttachmentCommon common = new AttachmentCommon(this.request, 1);
		common.preProcess();

		Post post = new Post();
		post.setId(100);
		common.insertAttachments(post);

		assertTrue(post.hasAttachments());
		assertEquals(1, this.mongo.findById(100, Document.class, "posts").get("attach"));

		List<Attachment> attachments = this.store.selectAttachments(100);
		assertEquals(2, attachments.size());

		Attachment image = attachments.stream().filter(a -> a.getInfo().getRealFilename().equals("cat.png")).findFirst().get();
		Attachment text = attachments.stream().filter(a -> a.getInfo().getRealFilename().equals("notes.txt")).findFirst().get();

		String physical = image.getInfo().getPhysicalFilename();
		assertTrue(physical.endsWith("_" + USER_ID + ".png_"), physical);
		assertEquals(png.length, image.getInfo().getFilesize());
		assertEquals("image/png", image.getInfo().getMimetype());
		assertEquals("comment 0", image.getInfo().getComment());

		assertArrayEquals(png, read(physical));
		assertArrayEquals(txt, read(text.getInfo().getPhysicalFilename()));

		assertTrue(image.hasThumb());
		assertFalse(text.hasThumb());

		BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(read(physical + "_thumb")));
		assertEquals(400, thumb.getWidth(), "resized to attachments.images.thumb.maxsize.w");
		assertEquals(200, thumb.getHeight());
		assertEquals("upload/" + physical + "_thumb", image.thumbPath());

		// removing through the edit form deletes metadata, file and thumbnail
		when(this.request.getParameter("delete_attach")).thenReturn(image.getId() + "," + text.getId());
		common.editAttachments(100, 1);

		assertTrue(this.store.selectAttachments(100).isEmpty());
		assertEquals(0, this.mongo.findById(100, Document.class, "posts").get("attach"));
		assertFalse(this.storage.exists(physical));
		assertFalse(this.storage.exists(physical + "_thumb"));
		assertFalse(this.storage.exists(text.getInfo().getPhysicalFilename()));
	}

	@Test
	void deniedExtensionsAreRejected()
	{
		this.files(new TestFileItem("file_0", "virus.exe", "application/octet-stream", new byte[10]));

		assertThrows(BadExtensionException.class, () -> new AttachmentCommon(this.request, 1).preProcess());
	}

	@Test
	void uploadsOverTheGroupQuotaAreRejected()
	{
		this.files(new TestFileItem("file_0", "a.txt", "text/plain", new byte[700]),
			new TestFileItem("file_1", "b.txt", "text/plain", new byte[700]));

		assertThrows(AttachmentSizeTooBigException.class, () -> new AttachmentCommon(this.request, 1).preProcess());
	}

	@Test
	void quotaIsTheBiggestOfTheUserGroups()
	{
		QuotaLimit ql = new AttachmentCommon(this.request, 1).getQuotaLimit(USER_ID);
		assertNotNull(ql);
		assertEquals(1024, ql.getSizeInBytes());
	}

	@Test
	void nothingIsStoredWhenAttachmentsAreDisabledForTheForum()
	{
		this.security.when(() -> SecurityRepository.canAccess(anyString(), anyString())).thenReturn(false);
		this.files(new TestFileItem("file_0", "a.txt", "text/plain", new byte[10]));

		AttachmentCommon common = new AttachmentCommon(this.request, 1);
		common.preProcess();

		Post post = new Post();
		post.setId(100);
		common.insertAttachments(post);

		assertTrue(this.store.selectAttachments(100).isEmpty());
	}

	private void files(TestFileItem... items)
	{
		when(this.request.getParameter("total_files")).thenReturn(String.valueOf(items.length));

		for (int i = 0; i < items.length; i++) {
			when(this.request.getObjectParameter("file_" + i)).thenReturn(items[i]);
			when(this.request.getParameter("comment_" + i)).thenReturn("comment " + i);
		}
	}

	private byte[] read(String filename) throws IOException
	{
		try (InputStream in = this.storage.openStream(filename)) {
			assertNotNull(in, filename);
			return in.readAllBytes();
		}
	}

	private static byte[] png(int w, int h) throws IOException
	{
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private record TestFileItem(String fieldName, String name, String contentType, byte[] content) implements FileItem
	{
		public String getName() { return this.name; }
		public String getFieldName() { return this.fieldName; }
		public String getContentType() { return this.contentType; }
		public long getSize() { return this.content.length; }
		public InputStream getInputStream() { return new ByteArrayInputStream(this.content); }
		public void write(File file) { throw new UnsupportedOperationException(); }
	}
}
