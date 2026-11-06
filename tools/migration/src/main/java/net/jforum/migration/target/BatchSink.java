package net.jforum.migration.target;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

/**
 * Buffers documents of one collection and writes them in batches of <code>batchSize</code>.
 */
public class BatchSink implements AutoCloseable
{
	private final Target target;
	private final String collection;
	private final int batchSize;
	private final List<Document> buffer;
	private long written;

	public BatchSink(Target target, String collection, int batchSize)
	{
		this.target = target;
		this.collection = collection;
		this.batchSize = batchSize;
		this.buffer = new ArrayList<>(batchSize);
	}

	public void add(Document doc)
	{
		this.buffer.add(doc);
		if (this.buffer.size() >= this.batchSize) {
			flush();
		}
	}

	public void flush()
	{
		if (!this.buffer.isEmpty()) {
			this.target.write(this.collection, this.buffer);
			this.written += this.buffer.size();
			this.buffer.clear();
		}
	}

	public long written()
	{
		return this.written;
	}

	@Override
	public void close()
	{
		flush();
	}
}
