package net.jforum.util.upload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * An uploaded file of a multipart request (replaces the vendored commons-fileupload FileItem).
 */
public interface FileItem
{
	/** @return the original file name, as sent by the browser */
	public String getName();

	public String getFieldName();

	public String getContentType();

	public long getSize();

	public InputStream getInputStream() throws IOException;

	public void write(File file) throws IOException;
}
