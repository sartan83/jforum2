package net.jforum.repository;

import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttachmentExtensionGroupRepository extends MongoRepository<AttachmentExtensionGroup, Integer> {
}
