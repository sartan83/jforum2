/*
 * Created on 04/09/2006 21:59:39
 */
package net.jforum.api.rest;

import net.jforum.persistence.ApiStore;
import net.jforum.persistence.Stores;

/**
 * @author Rafael Steil
 * @version $Id: RESTAuthentication.java,v 1.2 2006/10/10 00:49:04 rafaelsteil Exp $
 */
public class RESTAuthentication
{
	public boolean validateApiKey(String apiKey)
	{
		ApiStore dao = Stores.api();
		return dao.isValid(apiKey);
	}
}
