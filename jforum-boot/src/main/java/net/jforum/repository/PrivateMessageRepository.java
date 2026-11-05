package net.jforum.repository;

import java.util.Collection;
import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PrivateMessageRepository extends MongoRepository<PrivateMessage, Integer> {

    List<PrivateMessage> findByToUserIdAndTypeInOrderByDateDesc(Integer toUserId, Collection<PrivateMessageType> types);

    List<PrivateMessage> findByFromUserIdAndTypeInOrderByDateDesc(Integer fromUserId, Collection<PrivateMessageType> types);

    long countByToUserIdAndTypeIn(Integer toUserId, Collection<PrivateMessageType> types);
}
