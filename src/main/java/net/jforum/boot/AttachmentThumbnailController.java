package net.jforum.boot;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.persistence.mongo.AttachmentFileStorage;

import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the attachment thumbnails stored in GridFS at their legacy static URL
 * (<code>Attachment.thumbPath()</code> = <code>upload/&lt;physical filename&gt;_thumb</code>, used by
 * <code>post_show_attachments_inc.htm</code>). Only thumbnails are public: the files themselves
 * go through <code>posts/downloadAttach</code>, which checks the download permissions.
 */
@Controller
public class AttachmentThumbnailController
{
	private static final String PREFIX = "/upload/";

	@GetMapping(PREFIX + "**")
	public ResponseEntity<Resource> thumbnail(HttpServletRequest request) throws IOException
	{
		String path = request.getRequestURI().substring(request.getContextPath().length());
		String filename = path.substring(PREFIX.length());

		if (!filename.endsWith(AttachmentFileStorage.THUMB_SUFFIX)) {
			return ResponseEntity.notFound().build();
		}

		GridFsResource file = AttachmentFileStorage.get().open(filename);

		if (file == null) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.contentLength(file.contentLength())
			.cacheControl(CacheControl.noCache())
			.body(file);
	}
}
