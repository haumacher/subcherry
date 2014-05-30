package com.subcherry.trac;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.lustin.trac.xmlprc.Ticket;
import org.lustin.trac.xmlprc.TrackerDynamicProxy;
import org.lustin.trac.xmlprc.Wiki;

/**
 * @version $Revision$ $Author$ $Date$
 */
public class TracConnection {

	private static String CONF_ENTRY_URL = "URL";

	private static String CONF_ENTRY_NAME = "user";

	private static String CONF_ENTRY_PASSWORD = "password";

	private static TracConnection INSTANCE;

	private String userName;

	private String password;

	private String url;

	private TrackerDynamicProxy proxy;

	/** 
	 * Creates a new {@link TracConnection}.
	 * 
	 * @param aUrl
	 * @param aUserName
	 * @param aPassword
	 * @throws MalformedURLException
	 */
	public TracConnection(String aUrl, String aUserName, String aPassword) throws MalformedURLException {
		super();

		assert aUrl != null;
		assert aUserName != null;
		assert aPassword != null;

		this.userName = aUserName;
		this.password = aPassword;
		this.url = aUrl;

		XmlRpcClientConfigImpl theClientConfig = new XmlRpcClientConfigImpl();
		theClientConfig.setServerURL(new URL(url));

		theClientConfig.setBasicUserName(aUserName);
		theClientConfig.setBasicPassword(aPassword);

		XmlRpcClient theClient = new XmlRpcClient();
		theClient.setConfig(theClientConfig);

		this.proxy = new TrackerDynamicProxy(theClient);

	}

	public Ticket getTicket() {
		return (Ticket) this.proxy.newInstance(Ticket.class);
	}

	public Wiki getWiki() {
		return (Wiki) this.proxy.newInstance(Wiki.class);
	}
	
}
