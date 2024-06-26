/*
 *
 * Copyright (c) 2004 by Rodney Kinney
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
package VASSAL.chat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import VASSAL.build.GameModule;
import VASSAL.chat.messageboard.Message;
import VASSAL.chat.messageboard.MessageBoard;
import VASSAL.chat.peer2peer.PeerPoolInfo;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.tools.SequenceEncoder;

public class HttpMessageServer implements MessageBoard, WelcomeMessageServer {
  private static final Logger logger =
    LoggerFactory.getLogger(HttpMessageServer.class);

  private final HttpRequestWrapper welcomeURL;
  private final HttpRequestWrapper getMessagesURL;
  private final HttpRequestWrapper postMessageURL;
  private final PeerPoolInfo info;

  public HttpMessageServer(PeerPoolInfo info) {
    this(
      "https://vassalengine.org/util/getMessages", //$NON-NLS-1$
      "https://vassalengine.org/util/postMessage", //$NON-NLS-1$
      "https://vassalengine.org/util/motd",        //$NON-NLS-1$
      info
    );
  }

  public HttpMessageServer(String getMessagesURL, String postMessageURL, String welcomeURL, PeerPoolInfo info) {
    this.getMessagesURL = new HttpRequestWrapper(getMessagesURL);
    this.welcomeURL = new HttpRequestWrapper(welcomeURL);
    this.postMessageURL = new HttpRequestWrapper(postMessageURL);
    this.info = info;
  }

  @Override
  public Command getWelcomeMessage() {
    Command motd = new NullCommand();
    try {
      if (GameModule.getGameModule() != null) {
        for (final String s : welcomeURL.doGet(prepareInfo())) {
          motd = motd.append(GameModule.getGameModule().decode(s));
        }
      }
    }
    catch (final IOException e) {
      logger.error("IOException retrieving welcome message", e); //$NON-NLS-1$
    }
    return motd;
  }

  @Override
  public Message[] getMessages() {
    final List<Message> msgList = new ArrayList<>();
    try {
      for (final String msg : getMessagesURL.doGet(prepareInfo())) {
        try {
          final StringTokenizer st = new StringTokenizer(msg, "&"); //$NON-NLS-1$
          String s = st.nextToken();
          final String sender = s.substring(s.indexOf('=') + 1); //$NON-NLS-1$
          String date = st.nextToken();
          date = date.substring(date.indexOf('=') + 1); //$NON-NLS-1$
          s = st.nextToken(""); //$NON-NLS-1$

          String content = StringUtils.join(
            new SequenceEncoder.Decoder(s.substring(s.indexOf('=') + 1), '|'),
            '\n'
          );

          content = restorePercent(content);
          Date created;
          try {
            long time = Long.parseLong(date);
            final TimeZone t = TimeZone.getDefault();
            time += t.getOffset(Calendar.ERA, Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_YEAR, Calendar.DAY_OF_WEEK, Calendar.MILLISECOND);
            created = new Date(time);
          }
          catch (final NumberFormatException e) {
            created = new Date();
          }
          msgList.add(new Message(sender, content, created));
        }
        catch (final NoSuchElementException e) {
          logger.error("Badly formatted message in HttpMessageServer:  " + msg); //$NON-NLS-1$
        }
      }
    }
    catch (final IOException e) {
      logger.error("IOException retrieving messages", e); //$NON-NLS-1$
    }
    return msgList.toArray(new Message[0]);
  }

  private Properties prepareInfo() {
    final Properties p = new Properties();
    p.put("module", info.getModuleName()); //$NON-NLS-1$
    return p;
  }

  private String removePercent(String input) {
    final StringBuilder buff = new StringBuilder();
    final StringTokenizer st = new StringTokenizer(input, "%#", true); //$NON-NLS-1$
    while (st.hasMoreTokens()) {
      final String s = st.nextToken();
      switch (s.charAt(0)) {
      case '%':
        buff.append("/#/"); //$NON-NLS-1$
        break;
      case '#':
        buff.append("/##/"); //$NON-NLS-1$
        break;
      default:
        buff.append(s);
      }
    }
    return buff.toString();
  }

  private String restorePercent(String input) {
    for (int i = input.indexOf("/#/"); //$NON-NLS-1$
         i >= 0; i = input.indexOf("/#/")) { //$NON-NLS-1$
      input = input.substring(0, i) + "%" + input.substring(i + 3); //$NON-NLS-1$
    }
    for (int i = input.indexOf("/##/"); //$NON-NLS-1$
         i >= 0; i = input.indexOf("/##/")) { //$NON-NLS-1$
      input = input.substring(0, i) + "#" + input.substring(i + 4); //$NON-NLS-1$
    }
    return input;
  }

  @Override
  public void postMessage(String content) {
    if (content == null || content.length() == 0) {
      return;
    }
    content = removePercent(content);
    final SequenceEncoder se = new SequenceEncoder('|');
    final StringTokenizer st = new StringTokenizer(content, "\n\r"); //$NON-NLS-1$
    while (st.hasMoreTokens()) {
      se.append(st.nextToken());
    }
    final Properties p = prepareInfo();
    p.put("sender", info.getUserName()); //$NON-NLS-1$
    p.put("content", se.getValue()); //$NON-NLS-1$
    try {
      postMessageURL.doPost(p);
    }
    catch (final IOException e) {
      logger.error("IOException posting messages", e);
    }
  }
}
