package net.jforum.repository;

import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttachmentRepository extends MongoRepository<Attachment, Integer> {

    List<Attachment> findByPostId(Integer postId);

    List<Attachment> findByPrivmsgId(Integer privmsgId);
}
