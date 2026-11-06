package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.repository.ForumRepository;

import org.springframework.stereotype.Component;

/**
 * Ids of the forums the current user can access: the former <code>:fids:</code> placeholder,
 * filled by {@link ForumRepository#getListAllowedForums()}.
 */
@Component
public class ForumAccessResolver
{
	public List<Integer> allowedForumIds()
	{
		List<Integer> ids = new ArrayList<>();

		for (String id : ForumRepository.getListAllowedForums().split(",")) {
			ids.add(Integer.valueOf(id.trim()));
		}

		return ids;
	}
}
