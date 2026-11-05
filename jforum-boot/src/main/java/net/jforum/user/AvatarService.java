package net.jforum.user;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.mongodb.client.gridfs.model.GridFSFile;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.AvatarType;
import net.jforum.domain.User;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Avatars (legacy UserCommon.handleAvatar): uploaded images are resized to avatar.maxWidth x avatar.maxHeight
 * and stored in GridFS (bucket "fs", metadata.kind=avatar); external URLs when avatar.allow.external.url;
 * gallery images shipped in /images/avatar.
 */
@Service
public class AvatarService {

    private static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "gif", "png");

    private final GridFsTemplate gridFs;
    private final ForumConfig config;

    public AvatarService(GridFsTemplate gridFs, ForumConfig config) {
        this.gridFs = gridFs;
        this.config = config;
    }

    /** @return null on success, otherwise the i18n key of the error */
    public String upload(User u, MultipartFile file) {
        if (!config.getBool(ConfigKeys.AVATAR_ALLOW_UPLOAD) && config.get(ConfigKeys.AVATAR_ALLOW_UPLOAD) != null) {
            return "User.avatarUploadError";
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!EXTENSIONS.contains(ext)) {
            return "User.avatarUploadError";
        }
        if (file.getSize() > maxKbSize() * 1024L) {
            return "User.avatarTooBig";
        }
        BufferedImage image;
        try {
            image = ImageIO.read(file.getInputStream());
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            return "User.avatarUploadError";
        }
        BufferedImage resized = resize(image, config.getInt(ConfigKeys.AVATAR_MAX_WIDTH, 150), config.getInt(ConfigKeys.AVATAR_MAX_HEIGHT, 150));
        String format = ext.startsWith("jp") ? "jpg" : "png";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(resized, format, out);
        } catch (IOException e) {
            return "User.avatarUploadError";
        }
        deleteUploaded(u);
        Document meta = new Document("kind", "avatar").append("userId", u.getId());
        ObjectId id = gridFs.store(new ByteArrayInputStream(out.toByteArray()), "avatar-" + u.getId() + "." + format,
            "jpg".equals(format) ? "image/jpeg" : "image/png", meta);
        u.setAvatar(id.toHexString());
        u.setAvatarType(AvatarType.UPLOAD);
        return null;
    }

    /** @return null on success, otherwise the i18n key of the error */
    public String external(User u, String url) {
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "User.avatarUrlShouldHaveHttp";
        }
        if (url.contains("\"") || url.contains("<") || url.contains(">") || url.contains(" ")) {
            return "User.avatarUploadError";
        }
        deleteUploaded(u);
        u.setAvatar(url.trim());
        u.setAvatarType(AvatarType.EXTERNAL);
        return null;
    }

    public String gallery(User u, String fileName) {
        if (!gallery().contains(fileName)) {
            return "User.avatarUploadError";
        }
        deleteUploaded(u);
        u.setAvatar(fileName);
        u.setAvatarType(AvatarType.GALLERY);
        return null;
    }

    public void remove(User u) {
        deleteUploaded(u);
        u.setAvatar(null);
        u.setAvatarType(AvatarType.NONE);
    }

    public Optional<GridFsResource> uploaded(User u) {
        if (u.getAvatarType() != AvatarType.UPLOAD || u.getAvatar() == null || !ObjectId.isValid(u.getAvatar())) {
            return Optional.empty();
        }
        GridFSFile f = gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(u.getAvatar()))));
        return f == null ? Optional.empty() : Optional.of(gridFs.getResource(f));
    }

    /** File names of the gallery avatars in classpath:static/images/avatar. */
    public List<String> gallery() {
        List<String> names = new ArrayList<>();
        try {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:static/images/avatar/*")) {
                String n = r.getFilename();
                if (n != null && n.contains(".") && EXTENSIONS.contains(n.substring(n.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT))) {
                    names.add(n);
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        names.sort(String::compareTo);
        return names;
    }

    public int maxKbSize() {
        return config.getInt(ConfigKeys.AVATAR_MAX_KB_SIZE, config.getInt("avatarMaxKbSize", 25));
    }

    private void deleteUploaded(User u) {
        if (u.getAvatarType() == AvatarType.UPLOAD && u.getAvatar() != null && ObjectId.isValid(u.getAvatar())) {
            gridFs.delete(Query.query(Criteria.where("_id").is(new ObjectId(u.getAvatar()))));
        }
    }

    static BufferedImage resize(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        double ratio = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }
}
