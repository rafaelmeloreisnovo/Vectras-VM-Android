package com.vectras.vm.utils;

import java.util.ArrayList;
import java.util.List;

public class NumberUtils {

    public static int safeLongToInt(long l) {
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            return 0;
        }
        return (int) l;
    }

    /* --- Base conversion --- */

    /** Convert n to string in the given base (2–36). */
    public static String toBase(long n, int base) {
        if (base < 2 || base > 36) throw new IllegalArgumentException("base out of range 2-36");
        if (n == 0) return "0";
        boolean neg = n < 0;
        if (neg) n = -n;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (int)(n % base);
            sb.append(rem < 10 ? (char)('0' + rem) : (char)('a' + rem - 10));
            n /= base;
        }
        if (neg) sb.append('-');
        return sb.reverse().toString();
    }

    /** Parse a base-N string back to long. */
    public static long fromBase(String s, int base) {
        if (s == null || s.isEmpty() || base < 2 || base > 36)
            throw new IllegalArgumentException("invalid input");
        long result = 0;
        boolean neg = s.charAt(0) == '-';
        for (int i = neg ? 1 : 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int digit;
            if (c >= '0' && c <= '9') digit = c - '0';
            else if (c >= 'a' && c <= 'z') digit = c - 'a' + 10;
            else if (c >= 'A' && c <= 'Z') digit = c - 'A' + 10;
            else throw new IllegalArgumentException("invalid char: " + c);
            if (digit >= base) throw new IllegalArgumentException("digit " + digit + " >= base " + base);
            result = result * base + digit;
        }
        return neg ? -result : result;
    }

    /* --- Fibonacci: F(0)=0, F(1)=1, F(2)=1, F(3)=2 ... --- */

    public static long fibonacci(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;
        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) { long c = a + b; a = b; b = c; }
        return b;
    }

    public static long[] fibonacciSequence(int length) {
        long[] seq = new long[length];
        for (int i = 0; i < length; i++) seq[i] = fibonacci(i);
        return seq;
    }

    /* --- Tribonacci: T(0)=0, T(1)=0, T(2)=1, T(3)=1, T(4)=2, T(5)=4 ... --- */

    public static long tribonacci(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 0;
        if (n == 2) return 1;
        long a = 0, b = 0, c = 1;
        for (int i = 3; i <= n; i++) { long d = a + b + c; a = b; b = c; c = d; }
        return c;
    }

    public static long[] tribonacciSequence(int length) {
        long[] seq = new long[length];
        for (int i = 0; i < length; i++) seq[i] = tribonacci(i);
        return seq;
    }

    /* --- Primonacci: P(0)=2, P(1)=3, P(n)=next prime >= P(n-2)+P(n-1) --- */

    public static boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (long i = 3; i * i <= n; i += 2) if (n % i == 0) return false;
        return true;
    }

    public static long nextPrime(long n) {
        if (n < 2) return 2;
        long p = (n % 2 == 0) ? n + 1 : n + 2;
        while (!isPrime(p)) p += 2;
        return p;
    }

    public static long primonacci(int n) {
        if (n <= 0) return 2;
        if (n == 1) return 3;
        long a = 2, b = 3;
        for (int i = 2; i <= n; i++) {
            long sum = a + b;
            long p = isPrime(sum) ? sum : nextPrime(sum - 1);
            a = b; b = p;
        }
        return b;
    }

    public static long[] primonacciSequence(int length) {
        long[] seq = new long[length];
        for (int i = 0; i < length; i++) seq[i] = primonacci(i);
        return seq;
    }

    /* --- Pisano Period: Fibonacci mod m repeats with period P(m).
     * P(7)=16, P(10)=60, P(14)=24, P(70)=120 --- */

    public static int pisanoPeriod(int m) {
        if (m <= 1) return 1;
        long a = 0, b = 1;
        for (int i = 0; i < 6 * m; i++) {
            long c = (a + b) % m;
            a = b; b = c;
            if (a == 0 && b == 1) return i + 1;
        }
        return 0;
    }

    /* --- Base efficiency (radix economy).
     * economy(base, n) = ceil(log_base(n)) * base.
     * The theoretical minimum is at e ≈ 2.718; base 3 is nearest integer optimum. --- */

    public static double baseEfficiency(int base, long nMax) {
        if (base < 2 || nMax <= 0) return 0.0;
        double digits = Math.ceil(Math.log(nMax) / Math.log(base));
        return digits * base;
    }

    /** Returns radix-economy for a list of bases, indexed by the base value. */
    public static String baseEfficiencyReport(int[] bases, long nMax) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < bases.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(bases[i]).append('"')
              .append(':').append(String.format("%.4f", baseEfficiency(bases[i], nMax)));
        }
        return sb.append('}').toString();
    }

    /* --- Zero-curve dual: Z/aZ and Z/bZ coexist; they coincide at LCM(a,b) --- */

    public static int gcd(int a, int b) {
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a;
    }

    public static int lcm(int a, int b) {
        return (a / gcd(a, b)) * b;
    }

    /** JSON describing both modular rings and their coincidence points. */
    public static String zeroCurveDual(int baseA, int baseB) {
        int l = lcm(baseA, baseB);
        List<Integer> coincidences = new ArrayList<>();
        for (int i = 0; i <= l; i++) {
            if (i % baseA == 0 && i % baseB == 0) coincidences.add(i);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"base_a\":").append(baseA)
          .append(",\"base_b\":").append(baseB)
          .append(",\"lcm\":").append(l)
          .append(",\"pisano_a\":").append(pisanoPeriod(baseA))
          .append(",\"pisano_b\":").append(pisanoPeriod(baseB))
          .append(",\"ring_a\":[");
        for (int i = 0; i < baseA; i++) { if (i > 0) sb.append(','); sb.append(i); }
        sb.append("],\"ring_b\":[");
        for (int i = 0; i < baseB; i++) { if (i > 0) sb.append(','); sb.append(i); }
        sb.append("],\"coincidences\":[");
        for (int i = 0; i < coincidences.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(coincidences.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }
}
