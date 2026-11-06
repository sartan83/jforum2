package net.jforum.persistence.mongo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.jforum.document.PrivateMessageDocument;
import net.jforum.document.repository.PrivateMessageDocumentRepository;
import net.jforum.entities.Post;
import net.jforum.entities.PrivateMessage;
import net.jforum.entities.PrivateMessageType;
import net.jforum.entities.User;
import net.jforum.persistence.PrivateMessageStore;
import net.jforum.persistence.UserStore;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Same mailbox model as <code>GenericPrivateMessageDAO</code>: sending stores two copies, a
 * {@link PrivateMessageType#SENT} one for the sender and a {@link PrivateMessageType#NEW} one for the recipient.
 * The inbox shows NEW/READ/UNREAD copies addressed to the user, the sent box the SENT copies written by the user.
 */
@Component
public class MongoPrivateMessageStore implements PrivateMessageStore
{
	private static final List<Integer> INBOX_TYPES = List.of(
		PrivateMessageType.READ, PrivateMessageType.NEW, PrivateMessageType.UNREAD);

	private final PrivateMessageDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;
	private final ObjectProvider<UserStore> users;

	public MongoPrivateMessageStore(PrivateMessageDocumentRepository repository, MongoTemplate mongo,
		SequenceGenerator sequences, ObjectProvider<UserStore> users)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
		this.users = users;
	}

	@Override
	public void send(PrivateMessage pm)
	{
		// Sender's sent box
		this.insertCopy(pm, PrivateMessageType.SENT);
		// Target user's inbox: pm keeps this id, as in the legacy implementation
		this.insertCopy(pm, PrivateMessageType.NEW);
	}

	private void insertCopy(PrivateMessage pm, int type)
	{
		Post post = pm.getPost();
		PrivateMessageDocument doc = new PrivateMessageDocument();
		doc.setId(this.sequences.next(PrivateMessageDocument.COLLECTION));
		doc.setType(type);
		doc.setSubject(post.getSubject());
		doc.setFromUserId(pm.getFromUser().getId());
		doc.setToUserId(pm.getToUser().getId());
		doc.setDate(post.getTime() != null ? post.getTime() : new Date());
		doc.setIp("");
		doc.setEnableBbcode(post.isBbCodeEnabled());
		doc.setEnableHtml(post.isHtmlEnabled());
		doc.setEnableSmilies(post.isSmiliesEnabled());
		doc.setAttachSig(post.isSignatureEnabled());
		doc.setText(post.getText());
		this.repository.insert(doc);
		pm.setId(doc.getId());
	}

	/**
	 * Only the user's own copies can be deleted: sent copies written by him or
	 * read/new/unread copies addressed to him (<code>PrivateMessagesModel.isDeleteAllowed</code>).
	 */
	@Override
	public void delete(PrivateMessage[] pm, int userId)
	{
		for (PrivateMessage current : pm) {
			Criteria allowed = new Criteria().orOperator(
				Criteria.where("fromUserId").is(userId).and("type").is(PrivateMessageType.SENT),
				Criteria.where("toUserId").is(userId).and("type").in(INBOX_TYPES));

			this.mongo.remove(new Query(new Criteria().andOperator(Criteria.where("_id").is(current.getId()), allowed)),
				PrivateMessageDocument.class);
		}
	}

	@Override
	public void updateType(PrivateMessage pm)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(pm.getId())),
			new Update().set("type", pm.getType()), PrivateMessageDocument.class);
	}

	@Override
	public List selectFromInbox(User user)
	{
		return this.listing(Criteria.where("toUserId").is(user.getId()).and("type").in(INBOX_TYPES), "fromUserId", true);
	}

	@Override
	public List selectFromSent(User user)
	{
		return this.listing(Criteria.where("fromUserId").is(user.getId()).and("type").is(PrivateMessageType.SENT),
			"toUserId", false);
	}

	/**
	 * <code>PrivateMessageModel.baseListing</code>: newest first, joined with the other party's user
	 * (messages whose other party does not exist anymore are skipped, as with the legacy inner join).
	 */
	private List<PrivateMessage> listing(Criteria filter, String otherUserField, boolean inbox)
	{
		Aggregation agg = Aggregation.newAggregation(
			Aggregation.match(filter),
			Aggregation.sort(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("_id"))),
			Aggregation.lookup(ForeignCollections.USERS, otherUserField, "_id", "other"),
			Aggregation.unwind("other"),
			Aggregation.project("type", "date", "subject", otherUserField)
				.and("other." + ForeignCollections.USER_NAME).as("otherUsername"));

		SimpleDateFormat df = dateFormat();
		List<PrivateMessage> l = new ArrayList<>();

		for (Document d : this.mongo.aggregate(agg, PrivateMessageDocument.COLLECTION, Document.class)) {
			PrivateMessage pm = new PrivateMessage();
			pm.setId(((Number)d.get("_id")).intValue());
			pm.setType(d.getInteger("type", 0));

			Post p = new Post();
			p.setTime(d.getDate("date"));
			p.setSubject(d.getString("subject"));
			pm.setPost(p);
			pm.setFormatedDate(p.getTime() == null ? "" : df.format(p.getTime()));

			User other = new User();
			other.setId(((Number)d.get(otherUserField)).intValue());
			other.setUsername(d.getString("otherUsername"));

			if (inbox) {
				pm.setFromUser(other);
			}
			else {
				pm.setToUser(other);
			}

			l.add(pm);
		}

		return l;
	}

	@Override
	public PrivateMessage selectById(PrivateMessage pm)
	{
		PrivateMessageDocument doc = this.repository.findById(pm.getId()).orElse(null);

		if (doc == null) {
			return pm;
		}

		PrivateMessage result = new PrivateMessage();
		result.setId(doc.getId());
		result.setType(doc.getType());

		Post p = new Post();
		p.setTime(doc.getDate());
		p.setSubject(doc.getSubject());
		p.setBbCodeEnabled(doc.isEnableBbcode());
		p.setSignatureEnabled(doc.isAttachSig());
		p.setHtmlEnabled(doc.isEnableHtml());
		p.setSmiliesEnabled(doc.isEnableSmilies());
		p.setText(doc.getText());
		result.setPost(p);
		result.setFormatedDate(doc.getDate() == null ? "" : dateFormat().format(doc.getDate()));

		result.setFromUser(this.user(doc.getFromUserId()));
		result.setToUser(this.user(doc.getToUserId()));

		return result;
	}

	/**
	 * Full user through the {@link UserStore} when available, otherwise id + username read from <code>users</code>.
	 */
	private User user(int userId)
	{
		UserStore store = this.users.getIfAvailable();

		if (store != null) {
			return store.selectById(userId);
		}

		User u = new User();
		u.setId(userId);

		Query q = new Query(Criteria.where("_id").is(userId));
		q.fields().include(ForeignCollections.USER_NAME);
		Document d = this.mongo.findOne(q, Document.class, ForeignCollections.USERS);

		if (d != null) {
			u.setUsername(d.getString(ForeignCollections.USER_NAME));
		}

		return u;
	}

	private static SimpleDateFormat dateFormat()
	{
		String format = SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT);
		return new SimpleDateFormat(format == null ? "dd/MM/yyyy HH:mm" : format);
	}
}
