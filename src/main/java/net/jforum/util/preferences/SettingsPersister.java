package net.jforum.util.preferences;

import java.util.Properties;

/**
 * Storage of the installation specific overrides (values changed from the admin panel),
 * which win over the <code>jforum.*</code> defaults of <code>application.yml</code>.
 */
public interface SettingsPersister
{
	/**
	 * @return all stored overrides
	 */
	Properties load();

	/**
	 * Stores the given overrides (keys not present are left untouched).
	 */
	void save(Properties overrides);
}
