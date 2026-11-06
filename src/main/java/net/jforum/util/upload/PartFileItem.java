package net.jforum.util.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import jakarta.servlet.http.Part;

/**
 * {@link FileItem} backed by a Servlet 6 multipart {@link Part}.
 */
public class PartFileItem implements FileItem
{
	private final Part part;

	public PartFileItem(Part part)
	{
		this.part = part;
	}

	public String getName()
	{
		String name = this.part.getSubmittedFileName();

		if (name == null) {
			return null;
		}

		// old IE versions send the full client path
		int index = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		return index > -1 ? name.substring(index + 1) : name;
	}

	public String getFieldName()
	{
		return this.part.getName();
	}

	public String getContentType()
	{
		return this.part.getContentType();
	}

	public long getSize()
	{
		return this.part.getSize();
	}

	public InputStream getInputStream() throws IOException
	{
		return this.part.getInputStream();
	}

	public void write(File file) throws IOException
	{
		try (InputStream in = this.part.getInputStream()) {
			Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
