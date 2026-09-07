/*
 *
 * Copyright (c) 2000-2009 by Rodney Kinney, Joel Uckelman
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.tools.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link FilterInputStream} which converts a file created with
 * {@link ObfuscatingOutputStream} back into plain text.
 * Files in the legacy hex-encoded format are also handled, and
 * plain text will be passed through unchanged.
 *
 * @author Joel Uckelman
 * @since 3.2.0
 */
public class DeobfuscatingInputStream extends FilterInputStream {

  /** The header used by the hex-encoded format written before VASSAL 3.8. */
  private static final String LEGACY_HEADER = "!VCSK"; //NON-NLS

  /**
   * @param in the stream to wrap
   * @throws IOException
   */
  public DeobfuscatingInputStream(InputStream in) throws IOException {
    super(null);

    final int hlen = ObfuscatingOutputStream.HEADER.length();
    final byte[] header = new byte[Math.max(hlen, LEGACY_HEADER.length())];

    int n = readFully(in, header, 0, hlen);

    if (n == hlen) {
      if (ObfuscatingOutputStream.HEADER.equals(
            new String(header, 0, hlen, StandardCharsets.UTF_8))) {
        this.in = new DeobfuscatingInputStreamImpl(in);
        return;
      }

      n += readFully(in, header, hlen, LEGACY_HEADER.length() - hlen);

      if (n == LEGACY_HEADER.length() && LEGACY_HEADER.equals(
            new String(header, 0, n, StandardCharsets.UTF_8))) {
        this.in = new LegacyDeobfuscatingInputStreamImpl(in);
        return;
      }
    }

    // Not obfuscated; pass the whole stream through unchanged
    final PushbackInputStream pin =
      new PushbackInputStream(in, header.length);
    pin.unread(header, 0, n);
    this.in = pin;
  }

  /**
   * Reads the given number of bytes.
   *
   * @param in the source
   * @param bytes the destination
   * @param off the offset into the destination array
   * @param len the number of bytes to read
   * @return the number of bytes read
   * @throws IOException if an I/O error occurs
   */
  private static int readFully(InputStream in, byte[] bytes, int off, int len)
                                                           throws IOException {
    int count;
    int n = 0;
    while (n < len) {
      count = in.read(bytes, off + n, len - n);
      if (count < 0) break;
      n += count;
    }

    return n;
  }

  /**
   * Deobfuscates the format written by {@link ObfuscatingOutputStream}:
   * a one-byte key, followed by the data XORed with the key.
   */
  private static class DeobfuscatingInputStreamImpl extends FilterInputStream {
    private final byte key;

    public DeobfuscatingInputStreamImpl(InputStream in) throws IOException {
      super(in);

      final int k = in.read();
      if (k < 0) {
        throw new IOException("Truncated obfuscated stream: missing key"); //NON-NLS
      }
      key = (byte) k;
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
      final int n = in.read(bytes, off, len);
      for (int i = 0; i < n; ++i) {
        bytes[off + i] ^= key;
      }
      return n;
    }

    @Override
    public int read() throws IOException {
      final int b = in.read();
      return b < 0 ? -1 : (b ^ key) & 0xFF;
    }
  }

  /**
   * Deobfuscates the legacy format, in which the key and each data byte
   * were written as a pair of hex digits.
   */
  private static class LegacyDeobfuscatingInputStreamImpl extends FilterInputStream {
    private final byte key;
    private final byte[] pair = new byte[2];

    public LegacyDeobfuscatingInputStreamImpl(InputStream in) throws IOException {
      super(in);

      readFully(in, pair, 0, 2);
      key = (byte) ((unhex(pair[0]) << 4) | unhex(pair[1]));
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
      int b = 0;
      int i = 0;
      while (i < len && (b = read()) >= 0) bytes[(i++) + off] = (byte) b;
      return b == -1 && i == 0 ? -1 : i;
    }

    @Override
    public int read() throws IOException {
      switch (readFully(in, pair, 0, 2)) {
      case  0:
        return -1;
      case  2:
        return (((unhex(pair[0]) << 4) | unhex(pair[1])) ^ key) & 0xFF;
      case  1:
      default:
        throw new IOException();
      }
    }

    private int unhex(int i) throws IOException {
      switch (i) {
      // digits 0-9
      case 0x30:
      case 0x31:
      case 0x32:
      case 0x33:
      case 0x34:
      case 0x35:
      case 0x36:
      case 0x37:
      case 0x38:
      case 0x39:
        return i - 0x30;
      // digits A-F
      case 0x41:
      case 0x42:
      case 0x43:
      case 0x44:
      case 0x45:
      case 0x46:
        return i - 0x37;
      // digits a-f
      case 0x61:
      case 0x62:
      case 0x63:
      case 0x64:
      case 0x65:
      case 0x66:
        return i - 0x57;
      default:
        throw new IOException(String.valueOf(i));
      }
    }
  }

  public static void main(String[] args) throws IOException {
    try (InputStream in = new DeobfuscatingInputStream(
      args.length > 0 ? Files.newInputStream(Path.of(args[0])) : System.in)) {
      in.transferTo(System.out);
    }

    System.exit(0);
  }
}
