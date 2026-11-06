package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.jforum.document.AttachQuotaDocument;
import net.jforum.document.AttachmentDocument;
import net.jforum.document.ExtensionDocument;
import net.jforum.document.ExtensionGroupDocument;
import net.jforum.document.QuotaLimitDocument;
import net.jforum.document.repository.AttachQuotaDocumentRepository;
import net.jforum.document.repository.AttachmentDocumentRepository;
import net.jforum.document.repository.ExtensionDocumentRepository;
import net.jforum.document.repository.ExtensionGroupDocumentRepository;
import net.jforum.document.repository.QuotaLimitDocumentRepository;
import net.jforum.entities.Attachment;
import net.jforum.entities.AttachmentExtension;
import net.jforum.entities.AttachmentExtensionGroup;
import net.jforum.entities.AttachmentInfo;
import net.jforum.entities.QuotaLimit;
import net.jforum.persistence.AttachmentStore;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Attachment metadata, extensions, extension groups and quotas (former GenericAttachmentDAO).
 * File contents are handled by {@link AttachmentFileStorage}.
 */
@Component
public class MongoAttachmentStore implements AttachmentStore
{
	static final String ATTACH = "attach";
	static final String EXTENSIONS = "extensions";
	static final String EXTENSION_GROUPS = "extension_groups";
	static final String QUOTA_LIMIT = "quota_limit";
	static final String ATTACH_QUOTA = "attach_quota";

	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;
	private final AttachmentDocumentRepository attachments;
	private final ExtensionDocumentRepository extensions;
	private final ExtensionGroupDocumentRepository groups;
	private final QuotaLimitDocumentRepository quotas;
	private final AttachQuotaDocumentRepository groupQuotas;

	public MongoAttachmentStore(MongoTemplate mongo, SequenceGenerator sequences,
		AttachmentDocumentRepository attachments, ExtensionDocumentRepository extensions,
		ExtensionGroupDocumentRepository groups, QuotaLimitDocumentRepository quotas,
		AttachQuotaDocumentRepository groupQuotas)
	{
		this.mongo = mongo;
		this.sequences = sequences;
		this.attachments = attachments;
		this.extensions = extensions;
		this.groups = groups;
		this.quotas = quotas;
		this.groupQuotas = groupQuotas;
	}

	// ---------------------------------------------------------------- attachments

	public void addAttachment(Attachment a)
	{
		AttachmentInfo info = a.getInfo();

		AttachmentDocument d = new AttachmentDocument();
		d.id = this.sequences.next(ATTACH);
		d.postId = a.getPostId();
		d.privmsgsId = a.getPrivmsgsId();
		d.userId = a.getUserId();
		d.physicalFilename = info.getPhysicalFilename();
		d.realFilename = info.getRealFilename();
		d.description = info.getComment();
		d.mimetype = info.getMimetype();
		d.filesize = info.getFilesize();
		d.uploadTime = new Date(info.getUploadTimeInMillis());
		d.thumb = 0;
		d.extensionId = info.getExtension().getId();
		this.attachments.insert(d);

		a.setId(d.id);
		info.setAttachId(d.id);

		this.updatePost(a.getPostId(), true);
	}

	/** Only the comment and the download counter are updated, as in the legacy implementation. */
	public void updateAttachment(Attachment a)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(a.getId())),
			new Update()
				.set("description", a.getInfo().getComment())
				.set("downloadCount", a.getInfo().getDownloadCount()),
			AttachmentDocument.class);
	}

	public void removeAttachment(int id, int postId)
	{
		this.attachments.deleteById(id);
		this.updatePost(postId, this.attachments.countByPostId(postId) > 0);
	}

	public List selectAttachments(int postId)
	{
		List<Attachment> l = new ArrayList<>();

		for (AttachmentDocument d : this.attachments.findByPostIdOrderByIdAsc(postId)) {
			l.add(this.toAttachment(d));
		}

		return l;
	}

	public Attachment selectAttachmentById(int attachId)
	{
		return this.attachments.findById(attachId).map(this::toAttachment).orElse(null);
	}

	/** Sets <code>posts.attach</code> (owned by the posts area), keeping the flag type of the document. */
	private void updatePost(int postId, boolean hasAttachments)
	{
		Query q = new Query(Criteria.where(LegacyCollections.ID).is(postId));
		q.fields().include(LegacyCollections.Post.ATTACH);
		Document post = this.mongo.findOne(q, Document.class, LegacyCollections.POSTS);

		if (post != null) {
			this.mongo.updateFirst(new Query(Criteria.where(LegacyCollections.ID).is(postId)),
				new Update().set(LegacyCollections.Post.ATTACH,
					LegacyCollections.flagValue(post.get(LegacyCollections.Post.ATTACH), hasAttachments)),
				LegacyCollections.POSTS);
		}
	}

	private Attachment toAttachment(AttachmentDocument d)
	{
		Attachment a = new Attachment();
		a.setId(d.id);
		a.setPostId(d.postId);
		a.setPrivmsgsId(d.privmsgsId);
		a.setUserId(d.userId);

		AttachmentInfo ai = new AttachmentInfo();
		ai.setAttachId(d.id);
		ai.setComment(d.description);
		ai.setDownloadCount(d.downloadCount);
		ai.setFilesize(d.filesize);
		ai.setMimetype(d.mimetype);
		ai.setPhysicalFilename(d.physicalFilename);
		ai.setRealFilename(d.realFilename);

		if (d.uploadTime != null) {
			ai.setUploadTime(d.uploadTime);
			ai.setUploadTimeInMillis(d.uploadTime.getTime());
		}

		ai.setExtension(this.searchExtension(this.extensions.findById(d.extensionId)));
		a.setInfo(ai);

		return a;
	}

	// ---------------------------------------------------------------- quotas

	public void addQuotaLimit(QuotaLimit limit)
	{
		QuotaLimitDocument d = new QuotaLimitDocument();
		d.id = this.sequences.next(QUOTA_LIMIT);
		this.fill(d, limit);
		this.quotas.insert(d);
		limit.setId(d.id);
	}

	public void updateQuotaLimit(QuotaLimit limit)
	{
		this.quotas.findById(limit.getId()).ifPresent(d -> {
			this.fill(d, limit);
			this.quotas.save(d);
		});
	}

	private void fill(QuotaLimitDocument d, QuotaLimit limit)
	{
		d.quotaDesc = limit.getDescription();
		d.quotaLimit = limit.getSize();
		d.quotaType = limit.getType();
	}

	public void removeQuotaLimit(int id)
	{
		this.quotas.deleteById(id);
	}

	public void removeQuotaLimit(String[] ids)
	{
		for (String id : ids) {
			this.removeQuotaLimit(Integer.parseInt(id));
		}
	}

	public void setGroupQuota(int groupId, int quotaId)
	{
		AttachQuotaDocument d = new AttachQuotaDocument();
		d.id = this.sequences.next(ATTACH_QUOTA);
		d.groupId = groupId;
		d.quotaLimitId = quotaId;
		this.groupQuotas.insert(d);
	}

	public void cleanGroupQuota()
	{
		this.groupQuotas.deleteAll();
	}

	public List selectQuotaLimit()
	{
		List<QuotaLimit> l = new ArrayList<>();

		for (QuotaLimitDocument d : this.quotas.findAll(Sort.by("quotaType", "quotaLimit"))) {
			l.add(toQuotaLimit(d));
		}

		return l;
	}

	public QuotaLimit selectQuotaLimitByGroup(int groupId)
	{
		return this.groupQuotas.findFirstByGroupIdOrderByIdAsc(groupId)
			.flatMap(q -> this.quotas.findById(q.quotaLimitId))
			.map(MongoAttachmentStore::toQuotaLimit)
			.orElse(null);
	}

	public Map selectGroupsQuotaLimits()
	{
		Map<Integer, Integer> m = new HashMap<>();

		for (AttachQuotaDocument d : this.groupQuotas.findAll(Sort.by("_id"))) {
			m.put(d.groupId, d.quotaLimitId);
		}

		return m;
	}

	private static QuotaLimit toQuotaLimit(QuotaLimitDocument d)
	{
		QuotaLimit ql = new QuotaLimit();
		ql.setId(d.id);
		ql.setDescription(d.quotaDesc);
		ql.setSize(d.quotaLimit);
		ql.setType(d.quotaType);
		return ql;
	}

	// ---------------------------------------------------------------- extension groups

	public void addExtensionGroup(AttachmentExtensionGroup g)
	{
		ExtensionGroupDocument d = new ExtensionGroupDocument();
		d.id = this.sequences.next(EXTENSION_GROUPS);
		this.fill(d, g);
		this.groups.insert(d);
		g.setId(d.id);
	}

	public void updateExtensionGroup(AttachmentExtensionGroup g)
	{
		this.groups.findById(g.getId()).ifPresent(d -> {
			this.fill(d, g);
			this.groups.save(d);
		});
	}

	private void fill(ExtensionGroupDocument d, AttachmentExtensionGroup g)
	{
		d.name = g.getName();
		d.allow = g.isAllow() ? 1 : 0;
		d.uploadIcon = g.getUploadIcon();
		d.downloadMode = g.getDownloadMode();
	}

	public void removeExtensionGroups(String[] ids)
	{
		for (String id : ids) {
			this.groups.deleteById(Integer.parseInt(id));
		}
	}

	public List selectExtensionGroups()
	{
		List<AttachmentExtensionGroup> l = new ArrayList<>();

		for (ExtensionGroupDocument d : this.groups.findAll(Sort.by("name"))) {
			AttachmentExtensionGroup g = new AttachmentExtensionGroup();
			g.setId(d.id);
			g.setName(d.name);
			g.setUploadIcon(d.uploadIcon);
			g.setAllow(d.allow == 1);
			g.setDownloadMode(d.downloadMode);
			l.add(g);
		}

		return l;
	}

	public boolean isPhysicalDownloadMode(int extensionGroupId)
	{
		return this.groups.findById(extensionGroupId).map(g -> g.downloadMode == 2).orElse(true);
	}

	// ---------------------------------------------------------------- extensions

	/**
	 * Extensions whose group is denied are denied too; extensions without a (still existing)
	 * group are left out, as the legacy inner join did.
	 */
	public Map extensionsForSecurity()
	{
		Map<Integer, ExtensionGroupDocument> groupsById = new HashMap<>();
		this.groups.findAll().forEach(g -> groupsById.put(g.id, g));

		Map<String, Boolean> m = new HashMap<>();

		for (ExtensionDocument e : this.extensions.findAll()) {
			ExtensionGroupDocument g = groupsById.get(e.extensionGroupId);

			if (g == null) {
				continue;
			}

			int allow = g.allow == 1 ? e.allow : g.allow;
			m.put(e.extension, allow == 1);
		}

		return m;
	}

	public void addExtension(AttachmentExtension e)
	{
		ExtensionDocument d = new ExtensionDocument();
		d.id = this.sequences.next(EXTENSIONS);
		this.fill(d, e);
		this.extensions.insert(d);
		e.setId(d.id);
	}

	public void updateExtension(AttachmentExtension e)
	{
		this.extensions.findById(e.getId()).ifPresent(d -> {
			this.fill(d, e);
			this.extensions.save(d);
		});
	}

	private void fill(ExtensionDocument d, AttachmentExtension e)
	{
		d.extensionGroupId = e.getExtensionGroupId();
		d.description = e.getComment();
		d.uploadIcon = e.getUploadIcon();
		d.extension = e.getExtension().toLowerCase();
		d.allow = e.isAllow() ? 1 : 0;
	}

	public void removeExtensions(String[] ids)
	{
		for (String id : ids) {
			this.extensions.deleteById(Integer.parseInt(id));
		}
	}

	public List selectExtensions()
	{
		List<AttachmentExtension> l = new ArrayList<>();

		for (ExtensionDocument d : this.extensions.findAll(Sort.by("extension"))) {
			l.add(toExtension(d, ""));
		}

		return l;
	}

	public AttachmentExtension selectExtension(String extension)
	{
		return this.searchExtension(Optional.ofNullable(this.mongo.findOne(
			new Query(Criteria.where("extension").is(extension)).with(Sort.by("_id")), ExtensionDocument.class)));
	}

	/** The extension joined with its group (for the group icon), or an "unknown" extension. */
	private AttachmentExtension searchExtension(Optional<ExtensionDocument> found)
	{
		if (found.isPresent()) {
			Optional<ExtensionGroupDocument> group = this.groups.findById(found.get().extensionGroupId);

			if (group.isPresent()) {
				return toExtension(found.get(), group.get().uploadIcon);
			}
		}

		AttachmentExtension e = new AttachmentExtension();
		e.setUnknown(true);
		return e;
	}

	private static AttachmentExtension toExtension(ExtensionDocument d, String groupIcon)
	{
		AttachmentExtension e = new AttachmentExtension();
		e.setAllow(d.allow == 1);
		e.setComment(d.description);
		e.setExtension(d.extension);
		e.setExtensionGroupId(d.extensionGroupId);
		e.setId(d.id);

		String icon = d.uploadIcon;

		if (icon == null || icon.equals("")) {
			icon = groupIcon;
		}

		e.setUploadIcon(icon);
		return e;
	}
}
