// "Move 'return' to computation of the value of 'n'" "true"
class T {
    int f(int[] a, int b) {
        int n = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b) {
                return i;
            }
        }
        return n;
    }
}