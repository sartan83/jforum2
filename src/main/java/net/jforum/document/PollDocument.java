package net.jforum.document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_vote_desc</code>, with <code>jforum_vote_results</code> embedded as
 * <code>options</code> and <code>jforum_vote_voters</code> embedded as <code>voters</code>.
 */
@Document(collection = PollDocument.COLLECTION)
public class PollDocument
{
	public static final String COLLECTION = "polls";

	@Id
	public int id;
	@Indexed
	public int topicId;
	public String voteText;
	public Date voteStart;
	public int voteLength;
	public List<Option> options = new ArrayList<>();
	public List<Voter> voters = new ArrayList<>();

	public static class Option
	{
		public int voteOptionId;
		public String voteOptionText;
		public int voteResult;
	}

	public static class Voter
	{
		public int voteUserId;
		public String voteUserIp;
	}
}
