package com.zensols.util;

import java.util.Arrays;
import java.util.Set;
import java.util.Map;

/**
 * String utils used to create various low level NLP features.
*
 * @author Paul Landes
 * @author <a href="http://stackoverflow.com/questions/10355103/finding-the-longest-repeated-substring">Stack Overflow</a>
 */
public final class StringUtils {
    private StringUtils() {}

    /** Return the longest common prefix of s and t */
    private static String lcp(String s, String t, int limit) {
	int n = Math.min(Math.min(s.length(), t.length()), limit);
	for (int i = 0; i < n; i++) {
	    if (s.charAt(i) != t.charAt(i))
		return s.substring(0, i);
	}
	return s.substring(0, n);
    }

    /**
     * Return the longest repeated strings in s
     */
    public static Set<String> longestRepeatedString(String s) {
	return longestRepeatedString(s, Integer.MAX_VALUE);
    }

    /**
     * Return the longest repeated strings in s
     * @param limit max repeat string to return
     */
    public static Set<String> longestRepeatedString(String s, int limit) {
	// form the N suffixes
	int n  = s.length();
	String[] suffixes = new String[n];
	Set<String> repeats = new java.util.HashSet<String>();

	for (int i = 0; i < n; i++) {
	    suffixes[i] = s.substring(i, n);
	}

	// sort them
	Arrays.sort(suffixes);

	// find longest repeated substring by comparing adjacent sorted suffixes
	for (int i = 0; i < n-1; i++) {
	    String x = lcp(suffixes[i], suffixes[i+1], limit);
	    if (x.length() > 0) repeats.add(x);
	}

	return repeats;
    }

    /**
     * Return the the number of times <tt>findStr</tt> is repeated in
     * <tt>str</tt> <em>consecutively</em>
     */
    public static int countConsecutiveOccurs(String findStr, String str) {
	int idx = 0;
	int prevIdx = 0;
	int count = 0;
	int consecCount = 0;
	int maxConsec = 0;

	if (findStr.length() > 0) {
	    while (idx != -1) {
		idx = str.indexOf(findStr, idx);
		if (idx != -1) {
		    int fslen = findStr.length();
		    boolean consec = ((idx == 0) || (fslen == (idx - prevIdx)));
		    if (consec) consecCount++;
		    else {
			maxConsec = Math.max(maxConsec, consecCount);
			consecCount = 1;
		    }
		    //print("LI: (" + prevIdx + "->" + idx + "):" + consec + ", " + consecCount + ", " + maxConsec);
		    prevIdx = idx;
		    idx += fslen;
		    count++;
		}
	    }
	}

	return Math.max(maxConsec, consecCount);
    }

    /**
     * Return all unique characters found in <tt>s</tt>.
     */
    public static Set<Character> uniqueChars(String s) {
	Set<Character> chars = new java.util.HashSet<Character>();
	for (char c : s.toCharArray()) chars.add(c);
	return chars;
    }

    /**
     * Return unique character counts found in <tt>s</tt>.
     */
    public static Map<Character, Integer> uniqueCharCounts(String s) {
	Map<Character, Integer> chars = new java.util.HashMap<Character, Integer>();
	for (char c : s.toCharArray()) {
	    Integer cnt = chars.get(c);
	    if (cnt == null) chars.put(c, 1);
	    else chars.put(c, cnt + 1);
	}
	return chars;
    }

    /**
     * Count different kinds of capitalized tokens based on input sentence
     * <tt>tokens</tt>.  This returns a three dimension integer array with the
     * following:
     * <ul>
     * <li>number of first character being capital (i.e. <tt>Yes</tt>, <tt>YEs</tt>, <tt>YES</tt>)</li>
     * <li>number of capitalied tokens (i.e. <tt>Yes</tt>)</li>
     * <li>number of all caps tokens (i.e. <tt>YES</tt>)</li>
     */
    public static int[] countCapitals(String[] tokens) {
	int capCount = 0;
	int capitalizedCount = 0;
	int allCapsCount = 0;

	for (String tok : tokens) {
	    if (tok.length() > 0) {
		char[] ca = tok.toCharArray();
		boolean cap = Character.isUpperCase(ca[0]);
		boolean capitalized = cap;
		boolean allCaps = cap;

		for (int i = 1; i < ca.length; i++) {
		    if (Character.isUpperCase(ca[i])) {
			capitalized = false;
		    } else {
			allCaps = false;
		    }

		    if (!capitalized && !allCaps) break;
		}

		if (cap) capCount++;
		if (capitalized) capitalizedCount++;
		if (allCaps) allCapsCount++;
	    }
	}

	return new int[] { capCount, capitalizedCount, allCapsCount };
    }
}
