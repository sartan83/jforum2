package net.jforum.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

/**
 * Executes asynchronous tasks (e.g. e-mail spooling) in a small thread pool.
 *
 * @author Rafael Steil
 */
public class Executor
{
	private static final Logger logger = Logger.getLogger(Executor.class);
	private static final ExecutorService executor = Executors.newFixedThreadPool(5, r -> {
		Thread t = new Thread(r, "jforum-executor");
		t.setDaemon(true);
		return t;
	});

	public static void execute(Runnable runnable)
	{
		try {
			executor.execute(runnable);
		}
		catch (Exception e) {
			logger.error("Exception while running task: " + e, e);
		}
	}
}
