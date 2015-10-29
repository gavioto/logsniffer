/*******************************************************************************
 * logsniffer, open source tool for viewing, monitoring and analysing log data.
 * Copyright (c) 2015 Scaleborn UG, www.scaleborn.com
 *
 * logsniffer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * logsniffer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.logsniffer.util.grok;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GROK https://code.google.com/p/semicomplete/wiki/Grok pattern implementation.
 */
@JsonAutoDetect(creatorVisibility = Visibility.NONE, fieldVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public final class Grok {
	protected static final Pattern PATTERN_SUBGROK = Pattern.compile("%\\{([A-Z0-9_-]+)(?::([A-Z0-9_-]+))?\\}",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Pattern helper included from
	 * https://github.com/tony19/named-regexp/blob/master
	 * /src/main/java/com/google/code/regexp/Pattern.java.
	 * 
	 * @author mbok
	 * 
	 */
	private static final class PatternHelper {
		/**
		 * Determines if the character at the specified position of a string is
		 * escaped
		 * 
		 * @param s
		 *            string to evaluate
		 * @param pos
		 *            the position of the character to evaluate
		 * @return true if the character is escaped; otherwise false
		 */
		static private boolean isEscapedChar(final String s, final int pos) {
			return isSlashEscapedChar(s, pos) || isQuoteEscapedChar(s, pos);
		}

		/**
		 * Determines if the character at the specified position of a string is
		 * escaped with a backslash
		 * 
		 * @param s
		 *            string to evaluate
		 * @param pos
		 *            the position of the character to evaluate
		 * @return true if the character is escaped; otherwise false
		 */
		static private boolean isSlashEscapedChar(final String s, int pos) {

			// Count the backslashes preceding this position. If it's
			// even, there is no escape and the slashes are just literals.
			// If it's odd, one of the slashes (the last one) is escaping
			// the character at the given position.
			int numSlashes = 0;
			while (pos > 0 && s.charAt(pos - 1) == '\\') {
				pos--;
				numSlashes++;
			}
			return numSlashes % 2 != 0;
		}

		/**
		 * Determines if the character at the specified position of a string is
		 * quote-escaped (between \\Q and \\E)
		 * 
		 * @param s
		 *            string to evaluate
		 * @param pos
		 *            the position of the character to evaluate
		 * @return true if the character is quote-escaped; otherwise false
		 */
		static private boolean isQuoteEscapedChar(final String s, final int pos) {

			boolean openQuoteFound = false;
			boolean closeQuoteFound = false;

			// find last non-escaped open-quote
			String s2 = s.substring(0, pos);
			int posOpen = pos;
			while ((posOpen = s2.lastIndexOf("\\Q", posOpen - 1)) != -1) {
				if (!isSlashEscapedChar(s2, posOpen)) {
					openQuoteFound = true;
					break;
				}
			}

			if (openQuoteFound) {
				// search remainder of string (after open-quote) for a
				// close-quote;
				// no need to check that it's slash-escaped because it can't be
				// (the escape character itself is part of the literal when
				// quoted)
				if (s2.indexOf("\\E", posOpen) != -1) {
					closeQuoteFound = true;
				}
			}

			return openQuoteFound && !closeQuoteFound;
		}

		/**
		 * Determines if a string's character is within a regex character class
		 * 
		 * @param s
		 *            string to evaluate
		 * @param pos
		 *            the position of the character to evaluate
		 * @return true if the character is inside a character class; otherwise
		 *         false
		 */
		static private boolean isInsideCharClass(final String s, final int pos) {

			boolean openBracketFound = false;
			boolean closeBracketFound = false;

			// find last non-escaped open-bracket
			String s2 = s.substring(0, pos);
			int posOpen = pos;
			while ((posOpen = s2.lastIndexOf('[', posOpen - 1)) != -1) {
				if (!isEscapedChar(s2, posOpen)) {
					openBracketFound = true;
					break;
				}
			}

			if (openBracketFound) {
				// search remainder of string (after open-bracket) for a
				// close-bracket
				String s3 = s.substring(posOpen, pos);
				int posClose = -1;
				while ((posClose = s3.indexOf(']', posClose + 1)) != -1) {
					if (!isEscapedChar(s3, posClose)) {
						closeBracketFound = true;
						break;
					}
				}
			}

			return openBracketFound && !closeBracketFound;
		}

		/**
		 * Determines if the parenthesis at the specified position of a string
		 * is for a non-capturing group, which is one of the flag specifiers
		 * (e.g., (?s) or (?m) or (?:pattern). If the parenthesis is followed by
		 * "?", it must be a non- capturing group unless it's a named group
		 * (which begins with "?<"). Make sure not to confuse it with the
		 * lookbehind construct ("?<=" or "?<!").
		 *
		 * @param s
		 *            string to evaluate
		 * @param pos
		 *            the position of the parenthesis to evaluate
		 * @return true if the parenthesis is non-capturing; otherwise false
		 */
		static private boolean isNoncapturingParen(final String s, final int pos) {

			int len = s.length();
			boolean isLookbehind = false;

			// code-coverage reports show that pos and the text to
			// check never exceed len in this class, so it's safe
			// to not test for it, which resolves uncovered branches
			// in Cobertura

			if (pos >= 0 && pos + 4 < len) {
				String pre = s.substring(pos, pos + 4);
				isLookbehind = pre.equals("(?<=") || pre.equals("(?<!");
			}
			return pos >= 0 && pos + 2 < len && s.charAt(pos + 1) == '?' && (isLookbehind || s.charAt(pos + 2) != '<');
		}

		/**
		 * Counts the open-parentheses to the left of a string position,
		 * excluding escaped parentheses
		 * 
		 * @param s
		 *            string to evaluate
		 * @param pos
		 *            ending position of string; characters to the left of this
		 *            position are evaluated
		 * @return number of open parentheses
		 */
		static private int countOpenParens(final String s, final int pos) {
			java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\(");
			java.util.regex.Matcher m = p.matcher(s.subSequence(0, pos));

			int numParens = 0;

			while (m.find()) {
				// ignore parentheses inside character classes: [0-9()a-f]
				// which are just literals
				if (isInsideCharClass(s, m.start())) {
					continue;
				}

				// ignore escaped parens
				if (isEscapedChar(s, m.start())) {
					continue;
				}

				if (!isNoncapturingParen(s, m.start())) {
					numParens++;
				}
			}
			return numParens;
		}
	}

	private LinkedHashMap<String, Integer> groupNames = new LinkedHashMap<String, Integer>();
	private Pattern regexPattern;
	@JsonProperty
	private String grokPattern;
	private HashMap<Integer, GrokPredicate> groupPredicates;// = new
															// HashMap<Integer,
															// GrokPredicate>();

	/**
	 * Prohibit outside instantiation.
	 */
	private Grok() {
		super();
	}

	/**
	 * @return the regexPattern
	 */
	public GrokMatcher matcher(final CharSequence input) {
		return new GrokMatcher(this, regexPattern.matcher(input));
	}

	public LinkedHashMap<String, Integer> getGroupNames() {
		return groupNames;
	}

	/**
	 * @return the groupPredicates
	 */
	public HashMap<Integer, GrokPredicate> getGroupPredicates() {
		return groupPredicates;
	}

	/**
	 * @return the grokPattern
	 */
	public String getGrokPattern() {
		return grokPattern;
	}

	/**
	 * Compiles a grok pattern and generates an internal standard pattern
	 * representation for it.
	 * 
	 * @param registry
	 *            Groks registry for predefined types
	 * @param pattern
	 *            the grok pattern
	 * @param flags
	 *            flags corresponding to {@link Pattern#flags()}
	 * @return a compiled grok pattern
	 * @throws GrokException
	 */
	public static Grok compile(final GroksRegistry registry, final String pattern, final int flags)
			throws GrokException {
		Grok g = new Grok();
		g.grokPattern = pattern;
		StringBuilder compiledPattern = new StringBuilder();
		Matcher m = PATTERN_SUBGROK.matcher(pattern);
		int lastPos = 0;

		while (m.find()) {
			String left = pattern.substring(lastPos, m.start());
			lastPos = m.end();
			compiledPattern.append(left);
			int groupsCount = PatternHelper.countOpenParens(compiledPattern.toString(), compiledPattern.length());
			String subGrokName = m.group(1);
			String subGrokAttr = m.group(2);
			Grok subGrok = registry.getGroks().get(subGrokName);
			if (subGrok == null) {
				throw new GrokException(
						"No predefined Grok pattern for name '" + subGrokName + "' found used in pattern: " + pattern);
			}
			if (subGrokAttr != null) {
				compiledPattern.append("(");
				groupsCount++;
				g.groupNames.put(subGrokAttr, groupsCount);
			}
			compiledPattern.append(subGrok.regexPattern.pattern());
			if (subGrokAttr != null) {
				compiledPattern.append(")");
			}
			for (String subGrokSubAttr : subGrok.groupNames.keySet()) {
				g.groupNames.put(subGrokSubAttr, groupsCount + subGrok.groupNames.get(subGrokSubAttr));
			}
		}
		compiledPattern.append(pattern.substring(lastPos));
		// g.regexPattern = Pattern.compile(compiledPattern.toString(), flags);
		com.google.code.regexp.Pattern namedPattern = com.google.code.regexp.Pattern.compile(compiledPattern.toString(),
				flags);
		g.regexPattern = namedPattern.pattern();
		for (String name : namedPattern.groupInfo().keySet()) {
			g.groupNames.put(name, namedPattern.groupInfo().get(name).get(0).groupIndex() + 1);
		}
		return g;
	}

	/**
	 * Compiles a grok pattern and generates an internal standard pattern
	 * representation for it.
	 * 
	 * @param registry
	 *            Groks registry for predefined types
	 * @param pattern
	 *            the grok pattern
	 * @return a compiled grok pattern
	 * @throws GrokException
	 */
	public static Grok compile(final GroksRegistry registry, final String pattern) throws GrokException {
		return compile(registry, pattern, 0);
	}
}
