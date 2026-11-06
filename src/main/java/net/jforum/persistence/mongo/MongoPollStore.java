package net.jforum.persistence.mongo;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

import net.jforum.document.PollDocument;
import net.jforum.document.repository.PollDocumentRepository;
import net.jforum.entities.Poll;
import net.jforum.entities.PollOption;
import net.jforum.persistence.PollStore;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoPollStore implements PollStore
{
	private final PollDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;

	public MongoPollStore(PollDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
	}

	@Override
	public Poll selectById(int pollId)
	{
		return this.repository.findById(pollId).map(MongoPollStore::toPoll).orElse(null);
	}

	@Override
	public void delete(int pollId)
	{
		this.repository.deleteById(pollId);
	}

	@Override
	public void deleteByTopicId(int topicId)
	{
		this.mongo.remove(new Query(Criteria.where("topicId").is(topicId)), PollDocument.class);
	}

	@Override
	public void update(Poll poll)
	{
		PollDocument d = this.repository.findById(poll.getId()).orElse(null);

		if (d == null) {
			return;
		}

		d.voteText = poll.getLabel();
		d.voteLength = poll.getLength();

		if (poll.getChanges() != null) {
			for (Object o : poll.getChanges().getDeletedOptions()) {
				int optionId = ((PollOption)o).getId();
				d.options.removeIf(opt -> opt.voteOptionId == optionId);
			}

			for (Object o : poll.getChanges().getChangedOptions()) {
				PollOption changed = (PollOption)o;
				d.options.stream().filter(opt -> opt.voteOptionId == changed.getId())
					.forEach(opt -> opt.voteOptionText = changed.getText());
			}

			addOptions(d, poll.getChanges().getNewOptions());
		}

		this.mongo.updateFirst(new Query(Criteria.where("_id").is(d.id)),
			new Update().set("voteText", d.voteText).set("voteLength", d.voteLength).set("options", d.options),
			PollDocument.class);
	}

	@Override
	public int addNew(Poll poll)
	{
		PollDocument d = new PollDocument();
		d.id = this.sequences.next(PollDocument.COLLECTION);
		d.topicId = poll.getTopicId();
		d.voteText = poll.getLabel();
		d.voteLength = poll.getLength();
		d.voteStart = new Date();
		addOptions(d, poll.getOptions());
		this.repository.insert(d);

		poll.setId(d.id);
		poll.setStartTime(d.voteStart);
		return d.id;
	}

	@Override
	public void voteOnPoll(int pollId, int optionId, int userId, String ipAddress)
	{
		PollDocument.Voter voter = new PollDocument.Voter();
		voter.voteUserId = userId;
		voter.voteUserIp = ipAddress;

		this.mongo.updateFirst(
			new Query(Criteria.where("_id").is(pollId).and("options.voteOptionId").is(optionId)),
			new Update().inc("options.$.voteResult", 1).push("voters", voter),
			PollDocument.class);
	}

	@Override
	public boolean hasUserVotedOnPoll(int pollId, int userId)
	{
		return this.mongo.exists(new Query(Criteria.where("_id").is(pollId).and("voters.voteUserId").is(userId)),
			PollDocument.class);
	}

	@Override
	public boolean hasUserVotedOnPoll(int pollId, String ipAddress)
	{
		return this.mongo.exists(new Query(Criteria.where("_id").is(pollId).and("voters.voteUserIp").is(ipAddress)),
			PollDocument.class);
	}

	/**
	 * New options get ids after the current max option id of the poll (legacy <code>selectMaxVoteId</code>).
	 */
	private static void addOptions(PollDocument d, List options)
	{
		int next = d.options.stream().mapToInt(o -> o.voteOptionId).max().orElse(0);

		for (Object o : options) {
			PollOption option = (PollOption)o;
			PollDocument.Option opt = new PollDocument.Option();
			opt.voteOptionId = ++next;
			opt.voteOptionText = option.getText();
			opt.voteResult = 0;
			d.options.add(opt);
			option.setId(opt.voteOptionId);
			option.setPollId(d.id);
		}
	}

	private static Poll toPoll(PollDocument d)
	{
		Poll poll = new Poll();
		poll.setId(d.id);
		poll.setTopicId(d.topicId);
		poll.setLabel(d.voteText);
		poll.setStartTime(d.voteStart);
		poll.setLength(d.voteLength);

		d.options.stream().sorted(Comparator.comparingInt(o -> o.voteOptionId)).forEach(o -> {
			PollOption option = new PollOption();
			option.setPollId(d.id);
			option.setId(o.voteOptionId);
			option.setText(o.voteOptionText);
			option.setVoteCount(o.voteResult);
			poll.addOption(option);
		});

		return poll;
	}
}
