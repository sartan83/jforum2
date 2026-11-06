package net.jforum.migration;

import net.jforum.migration.report.ParityReport;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Runs the migration once at startup. Exit code: 0 when every parity check matches,
 * {@value #EXIT_MISMATCH} when the parity report contains a mismatch, 1 on errors.
 */
@Component
public class MigrationCommand implements CommandLineRunner, ExitCodeGenerator
{
	public static final int EXIT_MISMATCH = 2;

	private final Environment environment;
	private int exitCode;
	private ParityReport report;

	public MigrationCommand(Environment environment)
	{
		this.environment = environment;
	}

	@Override
	public void run(String... args) throws Exception
	{
		MigrationOptions options = MigrationOptions.from(this.environment);
		this.report = new Migration(options).run();
		this.exitCode = this.report.isOk() ? 0 : EXIT_MISMATCH;
	}

	@Override
	public int getExitCode()
	{
		return this.exitCode;
	}

	public ParityReport getReport()
	{
		return this.report;
	}
}
