package vn.svframe.svframelib.comp.adventure.gradient;

@FunctionalInterface
public interface Interpolator {
    Interpolator LINEAR = (start,end,count) -> values(start,end,count,0);
    Interpolator QUADRATIC_SLOW_TO_FAST = (start,end,count) -> values(start,end,count,1);
    Interpolator QUADRATIC_FAST_TO_SLOW = (start,end,count) -> values(start,end,count,2);

    double[] interpolate(double start, double end, int count);

    private static double[] values(double start, double end, int count, int mode) {
        int n = Math.max(0, count);
        double[] out = new double[n];
        if (n == 0) return out;
        if (n == 1) { out[0] = start; return out; }
        for (int i=0;i<n;i++) {
            double t = i/(double)(n-1);
            if (mode == 1) t *= t;
            else if (mode == 2) t = 1d - (1d-t)*(1d-t);
            out[i] = start + (end-start)*t;
        }
        return out;
    }
}
