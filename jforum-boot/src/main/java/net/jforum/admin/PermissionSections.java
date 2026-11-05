package net.jforum.admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.Role;
import net.jforum.i18n.I18n;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Builds the permission editor model from jforum/permissions.xml, like legacy XMLPermissionControl, and converts the
 * submitted form back into roles, like legacy PermissionProcessHelper.
 * <ul>
 * <li>single permissions: select "&lt;id&gt;$single" with deny/allow; "allow" is selected when the group has the role.</li>
 * <li>multiple permissions: multi-select "&lt;id&gt;" listing forums/categories; the SELECTED options are the restricted ones
 * (values NOT in the role), "all" means no restriction. Hidden "all&lt;id&gt;" carries every id separated by ';'.</li>
 * </ul>
 */
@Component
public class PermissionSections {

    private final CategoryRepository categories;
    private final ForumRepository forums;
    private final I18n i18n;
    private final List<SectionDef> definitions;

    public PermissionSections(CategoryRepository categories, ForumRepository forums, I18n i18n) {
        this.categories = categories;
        this.forums = forums;
        this.i18n = i18n;
        this.definitions = parse();
    }

    record PermissionDef(String id, String title, String type, String ref, List<String[]> options) {
    }

    record SectionDef(String id, String title, List<PermissionDef> permissions) {
    }

    public static final class Option {

        private final String id;
        private final String name;
        private final boolean selected;

        Option(String id, String name, boolean selected) {
            this.id = id;
            this.name = name;
            this.selected = selected;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isSelected() {
            return selected;
        }
    }

    public static final class Item {

        private final String id;
        private final String name;
        private final String type;
        private final List<Option> data;

        Item(String id, String name, String type, List<Option> data) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.data = data;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public List<Option> getData() {
            return data;
        }

        public boolean isAllowAll() {
            return data.stream().noneMatch(Option::isSelected);
        }

        public String getAllValues() {
            StringBuilder sb = new StringBuilder();
            data.forEach(o -> sb.append(';').append(o.getId()));
            return sb.toString();
        }
    }

    public static final class Section {

        private final String id;
        private final String sectionName;
        private final List<Item> permissions;

        Section(String id, String sectionName, List<Item> permissions) {
            this.id = id;
            this.sectionName = sectionName;
            this.permissions = permissions;
        }

        public String getId() {
            return id;
        }

        public String getSectionName() {
            return sectionName;
        }

        public List<Item> getPermissions() {
            return permissions;
        }
    }

    public List<Section> sections(Group group) {
        Map<String, Role> roles = new HashMap<>();
        group.getRoles().forEach(r -> roles.put(r.getName(), r));
        Map<String, List<String[]>> refs = refData();
        List<Section> result = new ArrayList<>();
        for (SectionDef s : definitions) {
            List<Item> items = new ArrayList<>();
            for (PermissionDef p : s.permissions()) {
                Role role = roles.get(p.id());
                List<Option> data = new ArrayList<>();
                if ("single".equals(p.type())) {
                    boolean already = false;
                    for (String[] o : p.options()) {
                        boolean selected = role != null && "allow".equals(o[0]) && !already;
                        already |= selected;
                        data.add(new Option(o[0], i18n.getMessage(o[1]), selected));
                    }
                } else {
                    for (String[] v : refs.getOrDefault(p.ref(), List.of())) {
                        boolean allowed = role != null && role.getValues().contains(v[0]);
                        data.add(new Option(v[0], v[1], !allowed));
                    }
                }
                items.add(new Item(p.id(), i18n.getMessage(p.title()), p.type(), data));
            }
            result.add(new Section(s.id(), i18n.getMessage(s.title()), items));
        }
        return result;
    }

    /** Replaces all roles of the group with the submitted permission form (legacy PermissionProcessHelper.processData). */
    public List<Role> rolesFromForm(Map<String, String[]> params) {
        Map<String, Role> roles = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            if (!name.startsWith("perm_") || values == null || values.length == 0) {
                continue;
            }
            if (name.endsWith("$single")) {
                if (!"allow".equals(values[0])) {
                    continue;
                }
                role(roles, name.substring(0, name.indexOf('$')));
            } else {
                Set<String> all = splitAll(params.get("all" + name));
                Set<String> allowed = new LinkedHashSet<>(all);
                if (!"all".equals(values[0])) {
                    allowed.removeAll(List.of(values));
                }
                role(roles, name).getValues().addAll(allowed);
            }
        }
        return new ArrayList<>(roles.values());
    }

    private static Role role(Map<String, Role> roles, String name) {
        return roles.computeIfAbsent(name, n -> {
            Role r = new Role();
            r.setName(n);
            return r;
        });
    }

    private static Set<String> splitAll(String[] raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw != null && raw.length > 0 && raw[0] != null) {
            for (String s : raw[0].split(";")) {
                if (!s.isBlank()) {
                    result.add(s.trim());
                }
            }
        }
        return result;
    }

    private Map<String, List<String[]>> refData() {
        Map<String, List<String[]>> refs = new HashMap<>();
        List<String[]> cats = new ArrayList<>();
        for (Category c : categories.findAllByOrderByOrderAsc()) {
            cats.add(new String[] {Integer.toString(c.getId()), c.getTitle()});
        }
        refs.put("categories", cats);
        List<String[]> fs = new ArrayList<>();
        Collection<Forum> all = forums.findAllByOrderByOrderAsc();
        for (Category c : categories.findAllByOrderByOrderAsc()) {
            for (Forum f : all) {
                if (c.getId().equals(f.getCategoryId())) {
                    fs.add(new String[] {Integer.toString(f.getId()), f.getName()});
                }
            }
        }
        refs.put("forums", fs);
        return refs;
    }

    private static List<SectionDef> parse() {
        try (InputStream in = new ClassPathResource("jforum/permissions.xml").getInputStream()) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document doc = f.newDocumentBuilder().parse(in);
            List<SectionDef> sections = new ArrayList<>();
            NodeList sectionNodes = doc.getDocumentElement().getElementsByTagName("section");
            for (int i = 0; i < sectionNodes.getLength(); i++) {
                Element s = (Element) sectionNodes.item(i);
                List<PermissionDef> perms = new ArrayList<>();
                NodeList pNodes = s.getElementsByTagName("permission");
                for (int j = 0; j < pNodes.getLength(); j++) {
                    Element p = (Element) pNodes.item(j);
                    String ref = null;
                    List<String[]> options = new ArrayList<>();
                    for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) {
                        if (n instanceof Element el) {
                            if ("sql".equals(el.getTagName())) {
                                ref = el.getAttribute("ref");
                            } else if ("option".equals(el.getTagName())) {
                                options.add(new String[] {el.getAttribute("value"), el.getAttribute("description")});
                            }
                        }
                    }
                    perms.add(new PermissionDef(p.getAttribute("id"), p.getAttribute("title"), p.getAttribute("type"), ref, options));
                }
                sections.add(new SectionDef(s.getAttribute("id"), s.getAttribute("title"), perms));
            }
            return sections;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse jforum/permissions.xml", e);
        }
    }
}
