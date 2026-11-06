package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jforum.boot.JForumApplication;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SequenceGeneratorTest extends MongoTestSupport
{
	@Autowired
	private SequenceGenerator sequences;

	@Test
	void generatesIncreasingIdsAndHonorsImportedMaximum()
	{
		int first = this.sequences.next("test_seq");
		assertEquals(first + 1, this.sequences.next("test_seq"));

		this.sequences.ensureAtLeast("test_seq", 100);
		assertEquals(101, this.sequences.next("test_seq"));

		this.sequences.ensureAtLeast("test_seq", 5);
		assertEquals(102, this.sequences.next("test_seq"));
	}
}
