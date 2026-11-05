package net.jforum.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.mongodb.client.gridfs.model.GridFSFile;

import net.jforum.domain.Smilie;
import net.jforum.format.SmilieService;
import net.jforum.repository.SmilieRepository;
import net.jforum.support.SequenceService;
import net.jforum.web.NotFoundException;

import org.bson.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Legacy SmiliesAction (module adminSmilies). Uploaded images are stored in GridFS (metadata type "smilie") and served
 * from the same /images/smilies/&lt;diskName&gt; URL as the bundled ones, falling back to the static classpath images.
 */
@Controller
public class SmiliesAdminController {

    private static final String TYPE = "smilie";

    private final SmilieRepository smilies;
    private final SmilieService smilieService;
    private final SequenceService sequences;
    private final GridFsTemplate gridFs;

    public SmiliesAdminController(SmilieRepository smilies, SmilieService smilieService, SequenceService sequences,
                                  GridFsTemplate gridFs) {
        this.smilies = smilies;
        this.smilieService = smilieService;
        this.sequences = sequences;
        this.gridFs = gridFs;
    }

    @GetMapping("/adminSmilies/list.page")
    public String list(Model model) {
        model.addAttribute("smilies", smilies.findAll());
        return "admin/smilie_list";
    }

    @GetMapping("/adminSmilies/insert.page")
    public String insert(Model model) {
        model.addAttribute("action", "insertSave");
        return "admin/smilie_form";
    }

    @GetMapping({"/adminSmilies/edit/{smilieId}.page", "/adminSmilies/edit.page"})
    public String edit(@PathVariable(required = false) Integer smilieId,
                       @RequestParam(value = "id", required = false) Integer id, Model model) {
        model.addAttribute("smilie", find(smilieId != null ? smilieId : id == null ? -1 : id));
        model.addAttribute("action", "editSave");
        return "admin/smilie_form";
    }

    @PostMapping("/adminSmilies/insertSave.page")
    public String insertSave(@RequestParam("code") String code,
                             @RequestParam(value = "smilie_img", required = false) MultipartFile image,
                             Model model) throws IOException {
        if (image == null || image.isEmpty()) {
            model.addAttribute("errorKey", "Smilies.Form.Url");
            model.addAttribute("action", "insertSave");
            return "admin/smilie_form";
        }
        Smilie s = new Smilie();
        s.setId(sequences.next("smilies"));
        s.setCode(AdminSupport.trim(code));
        store(s, image);
        smilies.save(s);
        smilieService.invalidate();
        return "redirect:/adminSmilies/list.page";
    }

    @PostMapping("/adminSmilies/editSave.page")
    public String editSave(@RequestParam("id") int id, @RequestParam("code") String code,
                           @RequestParam(value = "smilie_img", required = false) MultipartFile image) throws IOException {
        Smilie s = find(id);
        s.setCode(AdminSupport.trim(code));
        if (image != null && !image.isEmpty()) {
            store(s, image);
        }
        smilies.save(s);
        smilieService.invalidate();
        return "redirect:/adminSmilies/list.page";
    }

    @PostMapping("/adminSmilies/delete.page")
    public String delete(@RequestParam(value = "id", required = false) String[] ids) {
        for (Integer id : AdminSupport.ids(ids)) {
            smilies.findById(id).ifPresent(s -> {
                gridFs.delete(byName(s.getDiskName()));
                smilies.delete(s);
            });
        }
        smilieService.invalidate();
        return "redirect:/adminSmilies/list.page";
    }

    @GetMapping("/images/smilies/{name:.+}")
    public ResponseEntity<Resource> image(@PathVariable String name) throws IOException {
        if (name.contains("/") || name.contains("..")) {
            throw new NotFoundException("Not found");
        }
        GridFSFile file = gridFs.findOne(byName(name));
        Resource resource;
        if (file != null) {
            GridFsResource r = gridFs.getResource(file);
            resource = new InputStreamResource(r.getInputStream());
        } else {
            ClassPathResource r = new ClassPathResource("static/images/smilies/" + name);
            if (!r.exists()) {
                throw new NotFoundException("Not found");
            }
            resource = r;
        }
        MediaType type = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noCache()).body(resource);
    }

    private void store(Smilie s, MultipartFile image) throws IOException {
        String original = image.getOriginalFilename() == null ? "" : image.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ext.matches("gif|png|jpg|jpeg|webp")) {
            throw new IllegalArgumentException("Unsupported smilie image type");
        }
        String diskName = DigestUtils.md5DigestAsHex((original + System.nanoTime()).getBytes(StandardCharsets.UTF_8)) + "." + ext;
        if (s.getDiskName() != null) {
            gridFs.delete(byName(s.getDiskName()));
        }
        try (InputStream in = image.getInputStream()) {
            gridFs.store(in, diskName, MediaTypeFactory.getMediaType(diskName).map(MediaType::toString).orElse(null),
                new Document("type", TYPE));
        }
        s.setDiskName(diskName);
        s.setUrl("<img src=\"#CONTEXT#/images/smilies/" + diskName + "\" />");
    }

    private static Query byName(String name) {
        return Query.query(Criteria.where("filename").is(name).and("metadata.type").is(TYPE));
    }

    private Smilie find(int id) {
        return smilies.findById(id).orElseThrow(() -> new NotFoundException("Smilie not found"));
    }
}
