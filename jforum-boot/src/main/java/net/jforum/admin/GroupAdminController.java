package net.jforum.admin;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.domain.Group;
import net.jforum.repository.GroupRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.PermissionService;
import net.jforum.support.Ids;
import net.jforum.support.SequenceService;
import net.jforum.web.NotFoundException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy GroupAction (module adminGroups) including the permission editor. */
@Controller
public class GroupAdminController {

    private final GroupRepository groups;
    private final UserRepository users;
    private final GroupTree groupTree;
    private final PermissionSections sections;
    private final PermissionService permissions;
    private final SequenceService sequences;

    public GroupAdminController(GroupRepository groups, UserRepository users, GroupTree groupTree,
                                PermissionSections sections, PermissionService permissions, SequenceService sequences) {
        this.groups = groups;
        this.users = users;
        this.groupTree = groupTree;
        this.sections = sections;
        this.permissions = permissions;
        this.sequences = sequences;
    }

    @GetMapping("/adminGroups/list.page")
    public String list(Model model) {
        List<GroupTree.Node> nodes = groupTree.nodes();
        model.addAttribute("groups", nodes);
        model.addAttribute("deletable", nodes.stream().filter(n -> users.findByGroupIdsContaining(n.getId()).isEmpty())
            .map(GroupTree.Node::getId).toList());
        return "admin/group_list";
    }

    @GetMapping("/adminGroups/insert.page")
    public String insert(Model model) {
        model.addAttribute("groups", groupTree.nodes());
        model.addAttribute("action", "insertSave");
        return "admin/group_form";
    }

    @GetMapping("/adminGroups/edit/{groupId}.page")
    public String edit(@PathVariable int groupId, Model model) {
        model.addAttribute("group", find(groupId));
        model.addAttribute("groups", groupTree.nodes().stream().filter(n -> n.getId() != groupId).toList());
        model.addAttribute("action", "editSave");
        return "admin/group_form";
    }

    @PostMapping("/adminGroups/insertSave.page")
    public String insertSave(@RequestParam("group_name") String name,
                             @RequestParam(value = "group_description", required = false) String description,
                             @RequestParam(value = "parent_id", required = false) String parentId) {
        Group g = new Group();
        g.setId(sequences.next("groups"));
        fill(g, name, description, parentId);
        groups.save(g);
        return "redirect:/adminGroups/list.page";
    }

    @PostMapping("/adminGroups/editSave.page")
    public String editSave(@RequestParam("group_id") int groupId,
                           @RequestParam("group_name") String name,
                           @RequestParam(value = "group_description", required = false) String description,
                           @RequestParam(value = "parent_id", required = false) String parentId) {
        Group g = find(groupId);
        fill(g, name, description, parentId);
        if (g.getParentId() != null && g.getParentId() == groupId) {
            g.setParentId(0);
        }
        groups.save(g);
        return "redirect:/adminGroups/list.page";
    }

    private static void fill(Group g, String name, String description, String parentId) {
        g.setName(AdminSupport.trim(name));
        g.setDescription(AdminSupport.trim(description));
        Integer parent = Ids.parse(parentId);
        g.setParentId(parent == null ? 0 : parent);
    }

    @PostMapping("/adminGroups/delete.page")
    public String delete(@RequestParam(value = "group_id", required = false) String[] ids, Model model) {
        StringBuilder errors = new StringBuilder();
        for (Integer id : AdminSupport.ids(ids)) {
            if (!users.findByGroupIdsContaining(id).isEmpty()) {
                groups.findById(id).ifPresent(g -> errors.append(errors.isEmpty() ? "" : ", ").append(g.getName()));
                continue;
            }
            groups.deleteById(id);
        }
        permissions.invalidate();
        if (!errors.isEmpty()) {
            model.addAttribute("errorMessage", errors.toString());
        }
        return list(model);
    }

    @GetMapping("/adminGroups/permissions/{groupId}.page")
    public String permissions(@PathVariable int groupId, Model model) {
        Group g = find(groupId);
        model.addAttribute("group", g);
        model.addAttribute("sections", sections.sections(g));
        return "admin/group_permissions";
    }

    @PostMapping("/adminGroups/permissionsSave.page")
    public String permissionsSave(@RequestParam("id") int groupId, HttpServletRequest request) {
        Group g = find(groupId);
        Map<String, String[]> params = request.getParameterMap();
        g.setRoles(sections.rolesFromForm(params));
        groups.save(g);
        permissions.invalidate();
        return "redirect:/adminGroups/list.page";
    }

    private Group find(int id) {
        return groups.findById(id).orElseThrow(() -> new NotFoundException("Group not found"));
    }
}
