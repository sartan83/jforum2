package net.jforum.boot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;

import net.jforum.persistence.mongo.AttachmentFileStorage;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AttachmentThumbnailControllerTest extends MongoTestSupport
{
	@Autowired
	private AttachmentFileStorage storage;

	@Autowired
	private AttachmentThumbnailController controller;

	@Test
	void servesThumbnailsFromGridFsButNotTheOriginalFiles() throws Exception
	{
		byte[] thumb = { 1, 2, 3 };
		this.storage.store("2026/11/6/abc_2.png_", new ByteArrayInputStream(new byte[] { 9 }), "image/png", 1);
		this.storage.store("2026/11/6/abc_2.png__thumb", new ByteArrayInputStream(thumb), "image/jpeg", 1);

		ResponseEntity<Resource> ok = this.controller.thumbnail(request("/forum", "/forum/upload/2026/11/6/abc_2.png__thumb"));
		assertEquals(HttpStatus.OK, ok.getStatusCode());
		assertEquals("image/jpeg", ok.getHeaders().getContentType().toString());
		assertArrayEquals(thumb, ok.getBody().getInputStream().readAllBytes());

		assertEquals(HttpStatus.NOT_FOUND,
			this.controller.thumbnail(request("", "/upload/2026/11/6/abc_2.png_")).getStatusCode());
		assertEquals(HttpStatus.NOT_FOUND,
			this.controller.thumbnail(request("", "/upload/2026/11/6/missing_thumb")).getStatusCode());
	}

	private static MockHttpServletRequest request(String contextPath, String uri)
	{
		MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
		r.setContextPath(contextPath);
		return r;
	}
}
