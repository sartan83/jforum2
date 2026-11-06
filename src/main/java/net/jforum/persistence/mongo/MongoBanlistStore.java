package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.BanlistDocument;
import net.jforum.document.repository.BanlistDocumentRepository;
import net.jforum.entities.Banlist;
import net.jforum.persistence.BanlistStore;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link BanlistStore} (former <code>GenericBanlistDAO</code>).
 */
@Component
public class MongoBanlistStore implements BanlistStore
{
	private final BanlistDocumentRepository banlist;
	private final SequenceGenerator sequences;

	public MongoBanlistStore(BanlistDocumentRepository banlist, SequenceGenerator sequences)
	{
		this.banlist = banlist;
		this.sequences = sequences;
	}

	public void insert(Banlist b)
	{
		BanlistDocument d = new BanlistDocument();
		d.setId(this.sequences.next(BanlistDocument.COLLECTION));
		d.setUserId(b.getUserId());
		d.setBanlistIp(b.getIp());
		d.setBanlistEmail(b.getEmail());
		this.banlist.insert(d);

		b.setId(d.getId());
	}

	public void delete(int banlistId)
	{
		this.banlist.deleteById(banlistId);
	}

	public List selectAll()
	{
		List l = new ArrayList();

		for (BanlistDocument d : this.banlist.findAll(Sort.by("_id"))) {
			Banlist b = new Banlist();
			b.setId(d.getId());
			b.setUserId(d.getUserId() == null ? 0 : d.getUserId());
			b.setEmail(d.getBanlistEmail());
			b.setIp(d.getBanlistIp());
			l.add(b);
		}

		return l;
	}
}
