package io.github.dmytrostasyuk.packagetreesuite;

import java.util.Comparator;

/**
 * Orders names the way IDE project trees do: letters ignore case, and a run of digits compares by its numeric value,
 * so {@code Item2} comes before {@code Item10}. Characters other than letters and digits compare by their lower-case
 * code, which puts {@code _} before any letter ({@code step_one} before {@code steps}).
 */
final class NaturalOrder implements Comparator<String> {

    static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {
    }

    @Override
    public int compare(String first, String second) {
        int i = 0;
        int j = 0;
        while (i < first.length() && j < second.length()) {
            char a = first.charAt(i);
            char b = second.charAt(j);
            if (isDigit(a) && isDigit(b)) {
                int aEnd = endOfDigits(first, i);
                int bEnd = endOfDigits(second, j);
                int byValue = compareDigitRuns(first.substring(i, aEnd), second.substring(j, bEnd));
                if (byValue != 0) {
                    return byValue;
                }
                i = aEnd;
                j = bEnd;
                continue;
            }
            int byChar = Character.toLowerCase(a) - Character.toLowerCase(b);
            if (byChar != 0) {
                return byChar;
            }
            i++;
            j++;
        }
        int byLength = (first.length() - i) - (second.length() - j);
        /* Names equal apart from case still need a stable order: fall back to the case-sensitive one. */
        return byLength != 0 ? byLength : first.compareTo(second);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int endOfDigits(String text, int start) {
        int end = start;
        while (end < text.length() && isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    /* Compares two digit runs by value without parsing them, so runs longer than a long still work. */
    private static int compareDigitRuns(String first, String second) {
        String a = stripLeadingZeros(first);
        String b = stripLeadingZeros(second);
        if (a.length() != b.length()) {
            return a.length() - b.length();
        }
        return a.compareTo(b);
    }

    private static String stripLeadingZeros(String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }
}
