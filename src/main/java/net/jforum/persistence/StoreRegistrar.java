package net.jforum.persistence;

import net.jforum.boot.DemoData;
import net.jforum.boot.InstallationData;
import net.jforum.search.SearchFacade;
import net.jforum.search.SearchManager;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Publishes the Spring managed store beans (and the search manager) to the
 * static {@link Stores} / {@link SearchFacade} entry points used by the
 * legacy JForum code.
 */
@Component
public class StoreRegistrar implements SmartInitializingSingleton
{
	private final ApplicationContext context;

	public StoreRegistrar(ApplicationContext context)
	{
		this.context = context;
	}

	@Override
	public void afterSingletonsInstantiated()
	{
		Stores.clear();

		for (Class<?> type : Stores.TYPES) {
			register(type);
		}

		SearchFacade.init(this.context.getBeanProvider(SearchManager.class).getIfAvailable());

		if (Stores.isComplete()) {
			this.context.getBeanProvider(InstallationData.class).ifAvailable(InstallationData::installIfEmpty);
			this.context.getBeanProvider(DemoData.class).ifAvailable(DemoData::seed);
		}
	}

	private <T> void register(Class<T> type)
	{
		T store = this.context.getBeanProvider(type).getIfAvailable();

		if (store != null) {
			Stores.register(type, store);
		}
	}
}
