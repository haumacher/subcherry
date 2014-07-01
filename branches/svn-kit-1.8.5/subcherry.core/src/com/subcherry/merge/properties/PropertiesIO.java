/*
 * Copyright 1995-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.subcherry.merge.properties;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;

import com.subcherry.merge.properties.PropertiesEditor.Property;

/**
 * White-space-aware parsing and serialization of {@link Properties} files as defined in
 * {@link Properties#load(InputStream)} and {@link Properties#store(OutputStream, String)}.
 * 
 * @author Arthur van Hoff
 * @author Michael McCloskey
 * @author Xueming Shen
 * @author Bernhard Haumacher
 */
public class PropertiesIO {

    /**
	 * @see Properties#load(InputStream)
	 */
	public static void load(PropertiesEditor _editor, InputStream inStream) throws IOException {
		new PropertiesReader(_editor, inStream).readLine();
    }

	/* Read in a "logical line" from an InputStream/Reader, skip all comment
     * and blank lines and filter out those leading whitespace characters
     * (\u0020, \u0009 and \u000c) from the beginning of a "natural line".
     * Method returns the char length of the "logical line" and stores
     * the line in "lineBuf".
     */
	static class PropertiesReader {

		private final PropertiesEditor _editor;

		final InputStream _in;

		byte[] _inBuffer;

		/**
		 * Number of valid bytes in {@link #_inBuffer}.
		 */
		int _inSize = 0;

		/**
		 * Index of next byte to consume from {@link #_inBuffer}.
		 */
		int _inNext = 0;

		StringBuilder _lineBuf = new StringBuilder();

		int _keyLength = 0;
		StringBuilder _keyBuf = new StringBuilder();

		int _valueLength = 0;
		StringBuilder _valueBuf = new StringBuilder();

		// Skip all white space characters seen in the input.
		boolean skipWhiteSpace = true;

		// Whether a comment start character has been seen.
		boolean isCommentLine = false;

		boolean isNewLine = true;

		boolean appendedLineBegin = false;

		boolean precedingBackslash = false;

		boolean crSeen = false;

		// Whether currently the key part of the property assignment is being read.
		boolean inKey = true;

		public PropertiesReader(PropertiesEditor editor, InputStream in) {
			_editor = editor;
			_in = in;
            _inBuffer = new byte[8192];
        }

		void readLine() throws IOException {
			reset();
            while (true) {
				if (!bufferNext()) {
					return;
				}

				// Equivalent to using ISO-8859-1 decoder.
				char c = (char) (0xff & _inBuffer[_inNext++]);
				_lineBuf.append(c);

                if (skipWhiteSpace) {
                    if (c == ' ' || c == '\t' || c == '\f') {
                        continue;
                    }
                    if (!appendedLineBegin && (c == '\r' || c == '\n')) {
						if (c == '\n') {
							flushLine(false);
						}
                        continue;
                    }
                    skipWhiteSpace = false;
                    appendedLineBegin = false;
                }
                if (isNewLine) {
                    isNewLine = false;
                    if (c == '#' || c == '!') {
                        isCommentLine = true;
                        continue;
                    }
                }

                if (c != '\n' && c != '\r') {
					if (crSeen) {
						flushLine(false);
					}
					boolean nonWhiteSpace = ((c != ' ' && c != '\t' && c != '\f') || precedingBackslash);

					if (inKey) {
						if ((c == '=' || c == ':') && !precedingBackslash) {
							inKey = false;
							skipWhiteSpace = true;
							continue;
						}

						_keyBuf.append(c);
						if (nonWhiteSpace) {
							_keyLength = _keyBuf.length();
						}
					} else {
						_valueBuf.append(c);
						if (nonWhiteSpace) {
							_valueLength = _valueBuf.length();
						}
					}

                    //flip the preceding backslash flag
                    if (c == '\\') {
                        precedingBackslash = !precedingBackslash;
                    } else {
                        precedingBackslash = false;
                    }
				} else {
					// reached EOL
					if (c == '\r') {
						crSeen = true;
					} else {
						if (!flushLine(true)) {
							return;
						}
					}
                }
            }
        }

		private boolean flushLine(boolean testNext) throws IOException {
			crSeen = false;
			if (_keyBuf.length() == 0) {
				addComment();
				isCommentLine = false;
				isNewLine = true;
				skipWhiteSpace = true;
				inKey = true;
			} else {
				if (testNext) {
					if (!bufferNext()) {
						return false;
					}
				}
				if (precedingBackslash) {
					// Remove buffered backslash character.
					dropChar(inKey ? _keyBuf : _valueBuf);

					// Skip the leading whitespace characters in following line
					skipWhiteSpace = true;
					appendedLineBegin = true;
					precedingBackslash = false;
				} else {
					addBlock(isCommentLine);

					isCommentLine = false;
					isNewLine = true;
					skipWhiteSpace = true;
					inKey = true;
				}
			}
			return true;
		}

		private boolean bufferNext() throws IOException {
			if (_inNext >= _inSize) {
				// Read next chunk from _in.
				_inSize = _in.read(_inBuffer);
				_inNext = 0;
				if (_inSize <= 0) {
					// EOF reached.
					if (_lineBuf.length() > 0) {
						addBlock(isCommentLine);
					}
					return false;
				}
			}
			return true;
		}

		private void dropChar(StringBuilder charBuf) {
			charBuf.setLength(charBuf.length() - 1);
		}

		private void addBlock(boolean isComment) {
			if (isComment || _keyBuf.length() == 0) {
				addComment();
			} else {
				addProperty();
			}
		}

		private void addProperty() {
			_editor.addProperty(
				new Property(
					loadConvert(_keyBuf, _keyLength),
					loadConvert(_valueBuf, _valueLength),
					_lineBuf.toString()));
			reset();
		}

		private void addComment() {
			_editor.addProperty(new Property(null, null, _lineBuf.toString()));
			reset();
		}

		private void reset() {
			_lineBuf.setLength(0);

			_keyBuf.setLength(0);
			_keyLength = 0;

			_valueBuf.setLength(0);
			_valueLength = 0;
		}

		StringBuilder convtBuf = new StringBuilder();

		/**
		 * Converts encoded &#92;uxxxx to unicode chars and changes special saved chars to their
		 * original forms
		 * 
		 * @param length
		 *        Number of characters in the input to consume.
		 */
		private String loadConvert(StringBuilder in, int length) {
			int offset = 0;
			StringBuilder out = convtBuf;
			out.setLength(0);

			int end = offset + length;

			while (offset < end) {
				char c = in.charAt(offset++);
				if (c == '\\') {
					c = in.charAt(offset++);
					if (c == 'u') {
						// Read the xxxx
						int value = 0;
						for (int i = 0; i < 4; i++) {
							c = in.charAt(offset++);
							switch (c) {
								case '0':
								case '1':
								case '2':
								case '3':
								case '4':
								case '5':
								case '6':
								case '7':
								case '8':
								case '9':
									value = (value << 4) + c - '0';
									break;
								case 'a':
								case 'b':
								case 'c':
								case 'd':
								case 'e':
								case 'f':
									value = (value << 4) + 10 + c - 'a';
									break;
								case 'A':
								case 'B':
								case 'C':
								case 'D':
								case 'E':
								case 'F':
									value = (value << 4) + 10 + c - 'A';
									break;
								default:
									throw new IllegalArgumentException(
										"Malformed \\uxxxx encoding.");
							}
						}
						out.append((char) value);
					} else {
						if (c == 't')
							c = '\t';
						else if (c == 'r')
							c = '\r';
						else if (c == 'n')
							c = '\n';
						else if (c == 'f')
							c = '\f';
						out.append(c);
					}
				} else {
					out.append(c);
				}
			}

			return out.toString();
		}

    }

    /*
     * Converts unicodes to encoded &#92;uxxxx and escapes
     * special characters with a preceding slash
     */
	private static String saveConvert(String theString,
                               boolean escapeSpace,
                               boolean escapeUnicode) {
        int len = theString.length();
        int bufLen = len * 2;
        if (bufLen < 0) {
            bufLen = Integer.MAX_VALUE;
        }
        StringBuffer outBuffer = new StringBuffer(bufLen);

        for(int x=0; x<len; x++) {
            char aChar = theString.charAt(x);
            // Handle common case first, selecting largest block that
            // avoids the specials below
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append('\\'); outBuffer.append('\\');
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch(aChar) {
                case ' ':
                    if (x == 0 || escapeSpace)
                        outBuffer.append('\\');
                    outBuffer.append(' ');
                    break;
                case '\t':outBuffer.append('\\'); outBuffer.append('t');
                          break;
                case '\n':outBuffer.append('\\'); outBuffer.append('n');
                          break;
                case '\r':outBuffer.append('\\'); outBuffer.append('r');
                          break;
                case '\f':outBuffer.append('\\'); outBuffer.append('f');
                          break;
                case '=': // Fall through
                case ':': // Fall through
                case '#': // Fall through
                case '!':
                    outBuffer.append('\\'); outBuffer.append(aChar);
                    break;
                default:
                    if (((aChar < 0x0020) || (aChar > 0x007e)) & escapeUnicode ) {
                        outBuffer.append('\\');
                        outBuffer.append('u');
                        outBuffer.append(toHex((aChar >> 12) & 0xF));
                        outBuffer.append(toHex((aChar >>  8) & 0xF));
                        outBuffer.append(toHex((aChar >>  4) & 0xF));
                        outBuffer.append(toHex( aChar        & 0xF));
                    } else {
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }

    /**
	 * @see Properties#store(OutputStream, String)
	 */
	public static void store(PropertiesEditor editor, OutputStream out) throws IOException {
		internalStore(editor, new BufferedWriter(new OutputStreamWriter(out, "8859_1")), true);
    }

	private static void internalStore(PropertiesEditor editor, BufferedWriter bw, boolean escUnicode)
			throws IOException {
		boolean hasNewLine = true;
		for (Property property : editor.getProperties()) {
			String source = property.getSource();
			if (!hasNewLine) {
				bw.newLine();
			}
			if (source != null) {
				bw.write(source);
				hasNewLine = source.endsWith("\n") || source.endsWith("\r");
			} else {
				String key = property.getKey();
				String val = property.getValue();
				key = saveConvert(key, true, escUnicode);
				/* No need to escape embedded and trailing spaces for value, hence pass false to
				 * flag. */
				val = saveConvert(val, false, escUnicode);
				bw.write(key + " = " + val);
				bw.newLine();
				hasNewLine = true;
			}
        }
        bw.flush();
    }

    /**
     * Convert a nibble to a hex character
     * @param   nibble  the nibble to convert.
     */
    private static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }

    /** A table of hex digits */
    private static final char[] hexDigit = {
        '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'
    };
}
