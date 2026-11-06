/*
 * Created on 11/07/2005 00:25:19
 */
package net.jforum.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Remove special chars, spaces and etc from a string
 * @author Rafael Steil
 * @version $Id: URLNormalizerTest.java,v 1.4 2005/07/26 04:01:12 diegopires Exp $
 */
public class URLNormalizerTest
{
	@Test
	void testReplaceSpaceByUnderline()
	{
		String s = "this is a test";
		String normalized = URLNormalizer.normalize(s);
		
		assertEquals("this_is_a_test", normalized);
	}
	
	@Test
	
	void testFriendlyLimit()
	{
		String s = "this is long string used for testing the limit";
		String normalized = URLNormalizer.normalize(s);
		
		assertEquals("this_is_long_string_used_for_testing", normalized);
	}
	
	@Test
	
	void testUnfriendlyLimit()
	{
		String s = "this is long string used for testing the limit";
		String normalized = URLNormalizer.normalize(s, URLNormalizer.LIMIT, false);
		
		assertEquals("this_is_long_string_used_for_te", normalized);
	}
	
	@Test
	
	void testFriendlyLimitWithParentesis()
	{
		String s = "this is long string used for testing(the limit)";
		String normalized = URLNormalizer.normalize(s);
		
		assertEquals("this_is_long_string_used_for_testing", normalized);
	}
	
	@Test
	
	void testRemovePlusParentesis()
	{
		String s = "a test + some + 2 thing(s)";
		String normalized = URLNormalizer.normalize(s);
		
		assertEquals("a_test_some_2_things", normalized);
	}
	
	@Test
	
	void testRemovePorcentageDollarStarEtc()
	{
		String s = "!@#$%^&*";
		String normalized = URLNormalizer.normalize(s);
		
		assertEquals("", normalized);
	}
}
