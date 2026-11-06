/*
 * Copyright (c) JForum Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms,
 * with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the
 * following  disclaimer.
 * 2)  Redistributions in binary form must reproduce the
 * above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 * 3) Neither the name of "Rafael Steil" nor
 * the names of its contributors may be used to endorse
 * or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE
 */
package net.jforum;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import net.jforum.cache.CacheEngine;
import net.jforum.cache.Cacheable;
import net.jforum.exceptions.ForumException;
import net.jforum.exceptions.RepositoryStartupException;
import net.jforum.persistence.Stores;
import net.jforum.repository.ForumRepository;
import net.jforum.sso.LoginAuthenticator;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.apache.log4j.Logger;

/**
 * General utilities methods for loading configurations for JForum.
 * <p>
 * SystemGlobals itself is populated from the Spring environment
 * (<code>jforum.*</code> keys of <code>application.yml</code>) by
 * {@link net.jforum.boot.JForumEnvironment}.
 *
 * @author Rafael Steil
 */
public class ConfigLoader 
{
	private static final Logger logger = Logger.getLogger(ConfigLoader.class);
	private static CacheEngine cache;

	public static Properties loadModulesMapping(String baseConfigDir)
	{
		return loadProperties(baseConfigDir + "/modulesMapping.properties");
    }

	public static void createLoginAuthenticator()
	{
		String className = SystemGlobals.getValue(ConfigKeys.LOGIN_AUTHENTICATOR);

		try {
			LoginAuthenticator loginAuthenticator = (LoginAuthenticator)Class.forName(className)
				.getDeclaredConstructor().newInstance();
			SystemGlobals.setObjectValue(ConfigKeys.LOGIN_AUTHENTICATOR_INSTANCE, loginAuthenticator);
		}
		catch (Exception e) {
			throw new ForumException("Error while trying to create a login.authenticator instance ("
				+ className + "): " + e, e);
		}
	}

	public static void loadUrlPatterns()  
	{
		Properties p = loadProperties(SystemGlobals.getValue(ConfigKeys.CONFIG_DIR) + "/urlPattern.properties");

		for (Iterator iter = p.entrySet().iterator(); iter.hasNext(); ) {
			Map.Entry entry = (Map.Entry)iter.next();
			UrlPatternCollection.addPattern((String)entry.getKey(), (String)entry.getValue());
		}
    }

	public static void startCacheEngine()
	{
		try {
			String cacheImplementation = SystemGlobals.getValue(ConfigKeys.CACHE_IMPLEMENTATION);
			logger.info("Using cache engine: " + cacheImplementation);

			cache = (CacheEngine)Class.forName(cacheImplementation).getDeclaredConstructor().newInstance();
			cache.init();

			String s = SystemGlobals.getValue(ConfigKeys.CACHEABLE_OBJECTS);

			if (s == null || s.trim().equals("")) {
				logger.warn("Cannot find Cacheable objects to associate the cache engine instance.");
				return;
			}

			String[] cacheableObjects = s.split(",");

			for (int i = 0; i < cacheableObjects.length; i++) {
				Object o = Class.forName(cacheableObjects[i].trim()).getDeclaredConstructor().newInstance();

				if (o instanceof Cacheable) {
					((Cacheable)o).setCacheEngine(cache);
				}
				else {
					logger.error(cacheableObjects[i] + " is not an instance of net.jforum.cache.Cacheable");
				}
			}
		}
		catch (Exception e) {
			throw new ForumException("Error while starting the cache engine", e);
		}
	}

	public static void stopCacheEngine()
	{
		if (cache != null) {
			cache.stop();
		}
	}

	public static void startForumRepository()
	{
		try {
			ForumRepository.start(Stores.forum(), Stores.category(), Stores.config());
		}
		catch (Exception e) {
			logger.error("Unable to bootstrap JForum repository.", e);
			throw new RepositoryStartupException("Error while trying to start ForumRepository: " + e, e);
		}
	}

	private static Properties loadProperties(String filename)
	{
		Properties p = new Properties();

		try (FileInputStream fis = new FileInputStream(filename)) {
			p.load(fis);
			return p;
		}
		catch (IOException e) {
			throw new ForumException(e);
		}
	}
}
