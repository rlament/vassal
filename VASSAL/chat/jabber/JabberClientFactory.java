/*
 *
 * Copyright (c) 2000-2006 by Rodney Kinney
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.chat.jabber;

import java.util.Properties;
import VASSAL.build.GameModule;
import VASSAL.chat.ChatServerConnection;
import VASSAL.chat.ChatServerFactory;

/**
 * @author rkinney
 */
public class JabberClientFactory extends ChatServerFactory {
  public static final String JABBER_SERVER_TYPE = "jabber"; //$NON-NLS-1$
  public static final String JABBER_PWD = "jabberPassword"; //$NON-NLS-1$
  public static final String JABBER_LOGIN = "jabberPogin"; //$NON-NLS-1$
  public static final String JABBER_PORT = "jabberPort"; //$NON-NLS-1$
  public static final String JABBER_HOST = "jabberHost"; //$NON-NLS-1$
  public static final String DEFAULT_JABBER_PORT = "5222"; //$NON-NLS-1$
  public static final String DEFAULT_JABBER_HOST = "localhost"; //$NON-NLS-1$

  public ChatServerConnection buildServer(Properties serverConfig) {
    String host = serverConfig.getProperty(JABBER_HOST, DEFAULT_JABBER_HOST);
    int port = 5222;
    try {
      port = Integer.parseInt(serverConfig.getProperty(JABBER_PORT, DEFAULT_JABBER_PORT));
    }
    catch (NumberFormatException e) {
      e.printStackTrace();
    }
    String login = serverConfig.getProperty(JABBER_LOGIN, "anonymous"); //$NON-NLS-1$
    String password = serverConfig.getProperty(JABBER_PWD);
    return new JabberClient(GameModule.getGameModule(), host, port, login, password);
  }
}
