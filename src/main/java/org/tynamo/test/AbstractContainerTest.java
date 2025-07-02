/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.tynamo.test;

import static org.htmlunit.WebAssert.assertTextNotPresent;
import static org.htmlunit.WebAssert.assertTextPresent;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.testng.annotations.BeforeClass;
// import org.eclipse.jetty.server.nio.SelectChannelConnector;

public abstract class AbstractContainerTest
{
	protected static PauseableServer server;

	public static int port = 8180;

	protected static String BASEURI = "http://localhost:" + port + "/";

	protected final WebClient webClient = new WebClient();

	static String errorText = "You must correct the following errors before continuing";

	@BeforeClass
	public void startContainer() throws Exception
	{
		if (server == null)
		{
			String reserveNetworkPort = System.getProperty("reserved.network.port");

			if (reserveNetworkPort != null)
			{
				port = Integer.valueOf(reserveNetworkPort);
				BASEURI = "http://localhost:" + port + "/";
			} else {
				// adapted from http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
				// arbitrarily try next ten ports
				int maxPort = port + 10;
				for (port = port; port < maxPort; port++) {
					Socket sock = null;
					try {
						// Check if port is open by trying to connect as a client
						sock = new Socket("localhost", port);
						sock.close();
						continue;
					} catch (Exception e) {
						if (sock != null) sock = null;
						if (e.getMessage().contains("refused")) {
							break;
						}
						throw new RuntimeException("Couldn't find an available port to run the functional test server", e);
					}
				}

			}


			server = new PauseableServer();
			ServerConnector connector = new ServerConnector(server);
			connector.setPort(port);
			server.setConnectors(new Connector[]{connector});

			ContextHandlerCollection handlers = new ContextHandlerCollection();
			handlers.setHandlers(new ContextHandler[]{buildContext(), new ServletContextHandler()});
			server.setHandler(handlers);
			server.start();
			assertTrue(server.isStarted());
		}
	}

	/**
	 * Non-abstract hook method (with a default implementation) to allow subclasses to provide their own WebAppContext instance.
	 * @return a WebAppContext
	 * @throws URISyntaxException 
	 */
	public WebAppContext buildContext() throws URISyntaxException
	{
		WebAppContext context = new WebAppContext("src/main/webapp", "/");
//		if (new File("src/test/webapp").exists()) {
//			ResourceCollection resourceCollection = new ResourceCollection(new String[]{"src/main/webapp", "src/test/webapp"});
//			context.setBaseResource(resourceCollection);
//		}
		
		ResourceFactory resourceFactory = ResourceFactory.of(context);
		if (new File("src/test/webapp").exists()) {
//		    List<Resource> resources = new ArrayList<>();
//		    resources.add(resourceFactory.newResource("src/main/webapp"));
//		    resources.add(resourceFactory.newResource("src/test/webapp"));
//		    context.setBaseResource(resourceFactory.new newResource(resources));
		    List<URI> resources = new ArrayList<>();
		    resources.add(new URI("src/main/webapp"));
		    resources.add(new URI("src/test/webapp"));
		    context.setBaseResource(resourceFactory.newResource(resources));
		} else {
		    context.setBaseResource(resourceFactory.newResource("src/main/webapp"));
		}		
		

		/**
		 * like -Dorg.eclipse.jetty.webapp.parentLoaderPriority=true
		 * Sets the classloading model for the context to avoid an strange "ClassNotFoundException: org.slf4j.Logger"
		 */
		context.setParentLoaderPriority(true);
		return context;
	}

	public void pauseServer(boolean paused)
	{
		if (server != null) server.pause(paused);
	}

	public static class PauseableServer extends Server
	{
		public synchronized void pause(boolean paused)
		{
			try
			{
				if (paused) for (Connector connector : getConnectors())
					connector.stop();
				else for (Connector connector : getConnectors())
					connector.start();
			} catch (Exception e)
			{
			}
		}
	}

	/**
	 * Verifies that the specified xpath is somewhere on the page.
	 *
	 * @param page
	 * @param xpath
	 */
	protected void assertXPathPresent(HtmlPage page, String xpath)
	{
		String message = "XPath not present: " + xpath;
		List list = page.getByXPath(xpath);
		if (list.isEmpty()) fail(message);
		assertNotNull(list.get(0), message);
	}

	/**
	 * Verifies that the specified xpath does NOT appear anywhere on the page.
	 *
	 * @param page
	 * @param xpath
	 */
	protected void assertXPathNotPresent(HtmlPage page, String xpath)
	{
		if (!page.getByXPath(xpath).isEmpty()) fail("XPath IS present: " + xpath);
	}


	protected HtmlPage clickLink(HtmlPage page, String linkText)
	{
		try
		{
			return (HtmlPage) page.getAnchorByText(linkText).click();
		} catch (ElementNotFoundException e)
		{
			fail("Couldn't find a link with text '" + linkText + "' on page " + page);
		} catch (IOException e)
		{
			fail("Clicking on link '" + linkText + "' on page " + page + " failed because of: ", e);
		}
		return null;
	}

	protected HtmlPage clickButton(HtmlPage page, String buttonId) throws IOException
	{
		return page.getHtmlElementById(buttonId).click();
	}

	protected HtmlPage clickButton(HtmlForm form, String buttonValue) throws IOException
	{
		try
		{
			return form.<HtmlInput>getInputByValue(buttonValue).click();
		} catch (ElementNotFoundException e)
		{
			try
			{
				return form.getButtonByName(buttonValue).click();
			} catch (ElementNotFoundException e1)
			{
				fail("Couldn't find a button with text/name '" + buttonValue + "' on form '" + form.getNameAttribute() +
						"'");
			}
		}
		return null;
	}

	protected void assertErrorTextPresent(HtmlPage page)
	{
		assertTextPresent(page, errorText);
	}

	protected void assertErrorTextNotPresent(HtmlPage page)
	{
		assertTextNotPresent(page, errorText);
	}

}
