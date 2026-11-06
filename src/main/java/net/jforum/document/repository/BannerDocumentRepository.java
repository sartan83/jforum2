package net.jforum.document.repository;

import java.util.List;

import net.jforum.document.BannerDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BannerDocumentRepository extends MongoRepository<BannerDocument, Integer>
{
	List<BannerDocument> findByPlacementAndActiveTrueOrderByWeightAsc(int placement);
}
