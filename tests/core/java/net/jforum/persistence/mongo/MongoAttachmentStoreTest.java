package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import net.jforum.boot.JForumApplication;
import net.jforum.document.AttachQuotaDocument;
import net.jforum.document.AttachmentDocument;
import net.jforum.document.ExtensionDocument;
import net.jforum.document.ExtensionGroupDocument;
import net.jforum.document.QuotaLimitDocument;
import net.jforum.entities.Attachment;
import net.jforum.entities.AttachmentExtension;
import net.jforum.entities.AttachmentExtensionGroup;
import net.jforum.entities.AttachmentInfo;
import net.jforum.entities.QuotaLimit;
import net.jforum.persistence.AttachmentStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoAttachmentStoreTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private AttachmentFileStorage storage;

	private AttachmentStore store;
	private LegacyFixtures fixtures;

	@BeforeEach
	void setUp()
	{
		this.store = Stores.attachment();
		this.fixtures = new LegacyFixtures(this.mongo);
		this.fixtures.clear("posts");

		for (Class<?> c : List.of(AttachmentDocument.class, ExtensionDocument.class, ExtensionGroupDocument.class,
				QuotaLimitDocument.class, AttachQuotaDocument.class)) {
			this.mongo.remove(new Query(), c);
		}
	}

	@Test
	void registeredAsTheAttachmentStore()
	{
		assertTrue(this.store instanceof MongoAttachmentStore);
	}

	@Test
	void extensionGroupsAreSortedByNameAndUpdatable()
	{
		AttachmentExtensionGroup images = group("Images", true, 1);
		AttachmentExtensionGroup archives = group("Archives", true, 2);
		this.store.addExtensionGroup(images);
		this.store.addExtensionGroup(archives);

		List<AttachmentExtensionGroup> groups = this.store.selectExtensionGroups();
		assertEquals(List.of("Archives", "Images"), groups.stream().map(AttachmentExtensionGroup::getName).toList());

		assertFalse(this.store.isPhysicalDownloadMode(images.getId()));
		assertTrue(this.store.isPhysicalDownloadMode(archives.getId()));
		assertTrue(this.store.isPhysicalDownloadMode(9999), "unknown groups default to physical download");

		images.setName("Pictures");
		images.setDownloadMode(2);
		this.store.updateExtensionGroup(images);
		assertTrue(this.store.isPhysicalDownloadMode(images.getId()));

		this.store.removeExtensionGroups(new String[] { String.valueOf(archives.getId()) });
		groups = this.store.selectExtensionGroups();
		assertEquals(1, groups.size());
		assertEquals("Pictures", groups.get(0).getName());
	}

	@Test
	void extensionsAreLowercasedSortedAndJoinedWithTheirGroup()
	{
		AttachmentExtensionGroup images = group("Images", true, 1);
		images.setUploadIcon("images.gif");
		this.store.addExtensionGroup(images);

		AttachmentExtension png = extension(images.getId(), "PNG", true, null);
		AttachmentExtension gif = extension(images.getId(), "gif", true, "gif.gif");
		this.store.addExtension(png);
		this.store.addExtension(gif);

		assertEquals(List.of("gif", "png"),
			((List<AttachmentExtension>)this.store.selectExtensions()).stream().map(AttachmentExtension::getExtension).toList());

		AttachmentExtension found = this.store.selectExtension("png");
		assertFalse(found.isUnknown());
		assertEquals(png.getId(), found.getId());
		assertEquals("images.gif", found.getUploadIcon(), "falls back to the group icon");
		assertEquals("gif.gif", this.store.selectExtension("gif").getUploadIcon());
		assertTrue(this.store.selectExtension("exe").isUnknown());

		gif.setComment("GIF image");
		gif.setAllow(false);
		this.store.updateExtension(gif);
		assertFalse(this.store.selectExtension("gif").isAllow());

		this.store.removeExtensions(new String[] { String.valueOf(gif.getId()) });
		assertTrue(this.store.selectExtension("gif").isUnknown());
	}

	@Test
	void extensionsForSecurityHonorsGroupAndExtensionFlags()
	{
		AttachmentExtensionGroup allowed = group("Allowed", true, 1);
		AttachmentExtensionGroup denied = group("Denied", false, 1);
		this.store.addExtensionGroup(allowed);
		this.store.addExtensionGroup(denied);

		this.store.addExtension(extension(allowed.getId(), "jpg", true, null));
		this.store.addExtension(extension(allowed.getId(), "bat", false, null));
		this.store.addExtension(extension(denied.getId(), "exe", true, null));
		this.store.addExtension(extension(424242, "orphan", true, null));

		Map<String, Boolean> security = this.store.extensionsForSecurity();
		assertEquals(Map.of("jpg", true, "bat", false, "exe", false), security);
	}

	@Test
	void quotaLimitsAndGroupQuotas()
	{
		QuotaLimit big = quota("Big", 10, QuotaLimit.MB);
		QuotaLimit small = quota("Small", 500, QuotaLimit.KB);
		QuotaLimit medium = quota("Medium", 1, QuotaLimit.MB);
		this.store.addQuotaLimit(big);
		this.store.addQuotaLimit(small);
		this.store.addQuotaLimit(medium);

		assertEquals(List.of("Small", "Medium", "Big"),
			((List<QuotaLimit>)this.store.selectQuotaLimit()).stream().map(QuotaLimit::getDescription).toList(),
			"ordered by type, then limit");

		this.store.setGroupQuota(1, big.getId());
		this.store.setGroupQuota(2, small.getId());

		assertEquals(Map.of(1, big.getId(), 2, small.getId()), this.store.selectGroupsQuotaLimits());
		assertEquals(big.getId(), this.store.selectQuotaLimitByGroup(1).getId());
		assertEquals(10 * 1024 * 1024, this.store.selectQuotaLimitByGroup(1).getSizeInBytes());
		assertNull(this.store.selectQuotaLimitByGroup(3));

		big.setSize(20);
		big.setDescription("Bigger");
		this.store.updateQuotaLimit(big);
		assertEquals("Bigger", this.store.selectQuotaLimitByGroup(1).getDescription());
		assertEquals(20, this.store.selectQuotaLimitByGroup(1).getSize());

		this.store.cleanGroupQuota();
		assertTrue(this.store.selectGroupsQuotaLimits().isEmpty());

		this.store.removeQuotaLimit(medium.getId());
		this.store.removeQuotaLimit(new String[] { String.valueOf(small.getId()) });
		assertEquals(1, this.store.selectQuotaLimit().size());
	}

	@Test
	void attachmentsLifecycleKeepsThePostFlagInSync()
	{
		this.fixtures.post(10, 1, 1, 2, LegacyFixtures.at(1, 10), "s", "t");
		this.mongo.insert(new Document("_id", 11).append("attach", false), "posts");

		AttachmentExtensionGroup docs = group("Docs", true, 1);
		this.store.addExtensionGroup(docs);
		AttachmentExtension txt = extension(docs.getId(), "txt", true, null);
		this.store.addExtension(txt);

		Attachment first = attachment(10, "a.txt", txt);
		Attachment second = attachment(10, "b.txt", txt);
		Attachment other = attachment(11, "c.txt", txt);
		this.store.addAttachment(first);
		this.store.addAttachment(second);
		this.store.addAttachment(other);

		assertEquals(1, this.postField(10, "attach"));
		assertEquals(true, this.postField(11, "attach"), "boolean flags stay boolean");

		List<Attachment> attachments = this.store.selectAttachments(10);
		assertEquals(List.of("a.txt", "b.txt"), attachments.stream().map(a -> a.getInfo().getRealFilename()).toList());

		Attachment loaded = this.store.selectAttachmentById(first.getId());
		assertEquals(10, loaded.getPostId());
		assertEquals(2, loaded.getUserId());
		assertEquals("comment a.txt", loaded.getInfo().getComment());
		assertEquals("text/plain", loaded.getInfo().getMimetype());
		assertEquals(42, loaded.getInfo().getFilesize());
		assertEquals("txt", loaded.getInfo().getExtension().getExtension());
		assertEquals(docs.getId(), loaded.getInfo().getExtension().getExtensionGroupId());
		assertEquals(first.getInfo().getUploadTimeInMillis(), loaded.getInfo().getUploadTime().getTime());

		loaded.getInfo().setComment("new comment");
		loaded.getInfo().setDownloadCount(3);
		loaded.getInfo().setRealFilename("ignored.txt");
		this.store.updateAttachment(loaded);

		loaded = this.store.selectAttachmentById(first.getId());
		assertEquals("new comment", loaded.getInfo().getComment());
		assertEquals(3, loaded.getInfo().getDownloadCount());
		assertEquals("a.txt", loaded.getInfo().getRealFilename(), "only comment and download count are updated");

		this.store.removeAttachment(first.getId(), 10);
		assertEquals(1, this.postField(10, "attach"));
		this.store.removeAttachment(second.getId(), 10);
		assertEquals(0, this.postField(10, "attach"));
		this.store.removeAttachment(other.getId(), 11);
		assertEquals(false, this.postField(11, "attach"));

		assertNull(this.store.selectAttachmentById(first.getId()));
		assertTrue(this.store.selectAttachments(10).isEmpty());
	}

	@Test
	void gridFsStorageStoresReplacesAndDeletesFiles() throws Exception
	{
		this.storage.store("2026/1/1/abc_1.txt_", stream("first"), "text/plain", 1, "abc.txt");
		this.storage.store("2026/1/1/abc_1.txt_", stream("second"), "text/plain", 1, "abc.txt");

		assertTrue(this.storage.exists("2026/1/1/abc_1.txt_"));
		assertFalse(this.storage.exists("2026/1/1/abc_1.txt__thumb"));

		try (InputStream in = this.storage.openStream("2026/1/1/abc_1.txt_")) {
			assertEquals("second", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}

		Document file = this.mongo.getCollection(AttachmentFileStorage.BUCKET + ".files")
			.find(new Document("metadata.physicalFilename", "2026/1/1/abc_1.txt_")).first();
		assertEquals(1, this.mongo.getCollection(AttachmentFileStorage.BUCKET + ".files")
			.countDocuments(new Document("metadata.physicalFilename", "2026/1/1/abc_1.txt_")));
		assertEquals("abc.txt", file.getString("filename"));
		assertEquals(1, ((Document)file.get("metadata")).get("attachId"));
		assertEquals(false, ((Document)file.get("metadata")).get("thumb"));

		this.storage.delete("2026/1/1/abc_1.txt_");
		assertFalse(this.storage.exists("2026/1/1/abc_1.txt_"));
		assertNull(this.storage.open("2026/1/1/abc_1.txt_"));
	}

	@Test
	void filesImportedByTheMigrationToolAreFound() throws Exception
	{
		// tools/migration layout: bucket fs, filename = real name, metadata.physicalFilename / thumb
		org.springframework.data.mongodb.gridfs.GridFsTemplate fs = new org.springframework.data.mongodb.gridfs.GridFsTemplate(
			this.mongo.getMongoDatabaseFactory(), this.mongo.getConverter());
		fs.store(stream("original"), "photo.png", new Document("attachId", 5)
			.append("physicalFilename", "2007/3/1/xyz_2.png_").append("realFilename", "photo.png").append("thumb", false));
		fs.store(stream("thumb"), "photo.png_thumb", new Document("attachId", 5)
			.append("physicalFilename", "2007/3/1/xyz_2.png_").append("realFilename", "photo.png").append("thumb", true));

		try (InputStream in = this.storage.openStream("2007/3/1/xyz_2.png_")) {
			assertEquals("original", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}

		try (InputStream in = this.storage.openStream("2007/3/1/xyz_2.png__thumb")) {
			assertEquals("thumb", new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}

		this.storage.delete("2007/3/1/xyz_2.png__thumb");
		assertFalse(this.storage.exists("2007/3/1/xyz_2.png__thumb"));
		assertTrue(this.storage.exists("2007/3/1/xyz_2.png_"));
	}

	private Object postField(int postId, String field)
	{
		return this.mongo.findOne(new Query(Criteria.where("_id").is(postId)), Document.class, "posts").get(field);
	}

	private static InputStream stream(String s)
	{
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	static AttachmentExtensionGroup group(String name, boolean allow, int downloadMode)
	{
		AttachmentExtensionGroup g = new AttachmentExtensionGroup();
		g.setName(name);
		g.setAllow(allow);
		g.setDownloadMode(downloadMode);
		return g;
	}

	static AttachmentExtension extension(int groupId, String ext, boolean allow, String icon)
	{
		AttachmentExtension e = new AttachmentExtension();
		e.setExtensionGroupId(groupId);
		e.setExtension(ext);
		e.setAllow(allow);
		e.setUploadIcon(icon);
		e.setComment(ext + " files");
		return e;
	}

	static QuotaLimit quota(String description, int size, int type)
	{
		QuotaLimit q = new QuotaLimit();
		q.setDescription(description);
		q.setSize(size);
		q.setType(type);
		return q;
	}

	private static Attachment attachment(int postId, String name, AttachmentExtension ext)
	{
		Attachment a = new Attachment();
		a.setPostId(postId);
		a.setUserId(2);

		AttachmentInfo info = new AttachmentInfo();
		info.setRealFilename(name);
		info.setPhysicalFilename("2026/1/1/" + name + "_");
		info.setComment("comment " + name);
		info.setMimetype("text/plain");
		info.setFilesize(42);
		info.setUploadTimeInMillis(1767268800000L);
		info.setExtension(ext);
		a.setInfo(info);

		return a;
	}
}
