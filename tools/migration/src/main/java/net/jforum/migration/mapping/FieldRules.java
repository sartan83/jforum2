package net.jforum.migration.mapping;

import java.util.Map;
import java.util.Set;

import org.bson.Document;

/**
 * Adjusts the generic camelCase fields of a table to the field names/types of the application's
 * document class (<code>net.jforum.document.*</code>): an optional column prefix to strip
 * (<code>bannerName</code> → <code>name</code>), explicit renames (applied after stripping) and the
 * fields stored as BSON booleans (tinyint/int 0/1 in SQL Server).
 */
public record FieldRules(String stripPrefix, Map<String, String> renames, Set<String> booleans)
{
	public static final FieldRules NONE = new FieldRules(null, Map.of(), Set.of());

	public Document apply(Document doc)
	{
		if (this == NONE) {
			return doc;
		}
		Document shaped = new Document();
		for (Map.Entry<String, Object> e : doc.entrySet()) {
			String name = field(e.getKey());
			Object value = e.getValue();
			if (this.booleans.contains(name) && value instanceof Number n) {
				value = n.intValue() != 0;
			}
			shaped.put(name, value);
		}
		return shaped;
	}

	String field(String key)
	{
		if ("_id".equals(key)) {
			return key;
		}
		String name = key;
		if (this.stripPrefix != null && key.length() > this.stripPrefix.length() && key.startsWith(this.stripPrefix)
			&& Character.isUpperCase(key.charAt(this.stripPrefix.length()))) {
			String rest = key.substring(this.stripPrefix.length());
			name = Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
		}
		return this.renames.getOrDefault(name, name);
	}
}
