package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.BannerDocument;
import net.jforum.document.repository.BannerDocumentRepository;
import net.jforum.entities.Banner;
import net.jforum.persistence.BannerStore;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MongoBannerStore implements BannerStore
{
	private final BannerDocumentRepository repository;
	private final SequenceGenerator sequences;

	public MongoBannerStore(BannerDocumentRepository repository, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.sequences = sequences;
	}

	@Override
	public Banner selectById(int bannerId)
	{
		return this.repository.findById(bannerId).map(MongoBannerStore::toEntity).orElseGet(Banner::new);
	}

	@Override
	public List selectAll()
	{
		return toEntities(this.repository.findAll(Sort.by("comment").and(Sort.by("id"))));
	}

	@Override
	public int addNew(Banner banner)
	{
		BannerDocument doc = new BannerDocument();
		doc.setId(this.sequences.next(BannerDocument.COLLECTION));
		copy(banner, doc);
		this.repository.insert(doc);
		banner.setId(doc.getId());
		return doc.getId();
	}

	@Override
	public void update(Banner banner)
	{
		if (!this.repository.existsById(banner.getId())) {
			return;
		}

		BannerDocument doc = new BannerDocument();
		doc.setId(banner.getId());
		copy(banner, doc);
		this.repository.save(doc);
	}

	@Override
	public boolean canDelete(int bannerId)
	{
		return this.repository.existsById(bannerId);
	}

	@Override
	public void delete(int bannerId)
	{
		this.repository.deleteById(bannerId);
	}

	@Override
	public List selectActiveBannerByPlacement(int placement)
	{
		return toEntities(this.repository.findByPlacementAndActiveTrueOrderByWeightAsc(placement));
	}

	private static List<Banner> toEntities(List<BannerDocument> docs)
	{
		List<Banner> l = new ArrayList<>(docs.size());

		for (BannerDocument doc : docs) {
			l.add(toEntity(doc));
		}

		return l;
	}

	private static void copy(Banner b, BannerDocument doc)
	{
		doc.setName(b.getName());
		doc.setPlacement(b.getPlacement());
		doc.setDescription(b.getDescription());
		doc.setClicks(b.getClicks());
		doc.setViews(b.getViews());
		doc.setUrl(b.getUrl());
		doc.setWeight(b.getWeight());
		doc.setActive(b.isActive());
		doc.setComment(b.getComment());
		doc.setType(b.getType());
		doc.setWidth(b.getWidth());
		doc.setHeight(b.getHeight());
	}

	private static Banner toEntity(BannerDocument doc)
	{
		Banner b = new Banner();
		b.setId(doc.getId());
		b.setName(doc.getName());
		b.setPlacement(doc.getPlacement());
		b.setDescription(doc.getDescription());
		b.setClicks(doc.getClicks());
		b.setViews(doc.getViews());
		b.setUrl(doc.getUrl());
		b.setWeight(doc.getWeight());
		b.setActive(doc.isActive());
		b.setComment(doc.getComment());
		b.setType(doc.getType());
		b.setWidth(doc.getWidth());
		b.setHeight(doc.getHeight());
		return b;
	}
}
