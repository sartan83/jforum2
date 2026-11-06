package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.RankDocument;
import net.jforum.document.repository.RankDocumentRepository;
import net.jforum.entities.Ranking;
import net.jforum.persistence.RankingStore;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link RankingStore} (former <code>GenericRankingDAO</code>).
 */
@Component
public class MongoRankingStore implements RankingStore
{
	private final MongoTemplate mongo;
	private final RankDocumentRepository ranks;
	private final SequenceGenerator sequences;

	public MongoRankingStore(MongoTemplate mongo, RankDocumentRepository ranks, SequenceGenerator sequences)
	{
		this.mongo = mongo;
		this.ranks = ranks;
		this.sequences = sequences;
	}

	private static Ranking toRanking(RankDocument d)
	{
		Ranking ranking = new Ranking();
		ranking.setId(d.getId());
		ranking.setTitle(d.getRankTitle());
		ranking.setImage(d.getRankImage());
		ranking.setMin(d.getRankMin() == null ? 0 : d.getRankMin());
		ranking.setSpecial(d.getRankSpecial() != null && d.getRankSpecial() == 1);
		return ranking;
	}

	private List toRankings(List<RankDocument> docs)
	{
		List l = new ArrayList(docs.size());
		docs.forEach(d -> l.add(toRanking(d)));
		return l;
	}

	public Ranking selectById(int rankingId)
	{
		return this.ranks.findById(rankingId).map(MongoRankingStore::toRanking).orElseGet(Ranking::new);
	}

	public List selectAll()
	{
		return this.toRankings(this.mongo.find(new Query().with(Sort.by("rankMin", "_id")), RankDocument.class));
	}

	public List selectSpecials()
	{
		return this.toRankings(this.mongo.find(new Query(Criteria.where("rankSpecial").is(1)).with(Sort.by("_id")),
			RankDocument.class));
	}

	public void delete(int rankingId)
	{
		this.ranks.deleteById(rankingId);
	}

	public void update(Ranking ranking)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(ranking.getId())),
			new Update().set("rankTitle", ranking.getTitle())
				.set("rankImage", ranking.getImage())
				.set("rankSpecial", ranking.isSpecial() ? 1 : 0)
				.set("rankMin", ranking.getMin()),
			RankDocument.class);
	}

	public void addNew(Ranking ranking)
	{
		RankDocument d = new RankDocument();
		d.setId(this.sequences.next(RankDocument.COLLECTION));
		d.setRankTitle(ranking.getTitle());
		d.setRankMin(ranking.getMin());
		d.setRankSpecial(ranking.isSpecial() ? 1 : 0);
		d.setRankImage(ranking.getImage());
		this.ranks.insert(d);

		ranking.setId(d.getId());
	}
}
