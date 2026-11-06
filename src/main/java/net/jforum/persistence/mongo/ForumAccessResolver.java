package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.jforum.repository.ForumRepository;

import org.springframework.stereotype.Component;

/**
 * Ids of the forums the current user can access: the former <code>:fids:</code> placeholder,
 * filled by {@link ForumRepository#getListAllowedForums()}.
 */
@Component
public class ForumAccessResolver
{
	private Supplier<String> allowedForums = ForumRepository::getListAllowedForums;

	public List<Integer> allowedForumIds()
	{
		List<Integer> ids = new ArrayList<>();

		for (String id : this.allowedForums.get().split(",")) {
			if (!id.isBlank()) {
				ids.add(Integer.valueOf(id.trim()));
			}
		}

		return ids;
	}

	/** Replaces the source of the allowed forum ids (comma separated); <code>null</code> restores the default. */
	void setAllowedForums(Supplier<String> allowedForums)
	{
		this.allowedForums = allowedForums != null ? allowedForums : ForumRepository::getListAllowedForums;
	}
}
