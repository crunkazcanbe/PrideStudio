package com.dogpound.pridestudio.edit;

import net.minecraft.util.math.BlockPos;

/** Shapes built around a point: sphere, dome, cylinder, cone, pyramid, cube, torus, helix, arch, ellipsoid. */
public final class ShapeOps {
    private ShapeOps() {}

    public interface Inside { boolean at(int x, int y, int z); }

    /** fill every block of the box -r..r where `in` says yes; hollow keeps only blocks with an outside neighbour */
    static int build(EditSession es, BlockPos o, int rx, int ylo, int yhi, int rz, Pattern pat, boolean hollow, Inside in) {
        int n = 0;
        for (int x = -rx; x <= rx; x++) for (int y = ylo; y <= yhi; y++) for (int z = -rz; z <= rz; z++) {
            if (!in.at(x, y, z)) continue;
            if (hollow && in.at(x + 1, y, z) && in.at(x - 1, y, z) && in.at(x, y + 1, z) && in.at(x, y - 1, z) && in.at(x, y, z + 1) && in.at(x, y, z - 1)) continue;
            if (!es.set(o.add(x, y, z), pat.next())) return n;
            n++;
        }
        return n;
    }

    public static int run(EditSession es, String shape, BlockPos o, Pattern pat, int r, int h, int r2, int turns, boolean hollow) {
        final double R = r + 0.5, R2 = R * R;
        switch (shape) {
            case "sphere": return build(es, o, r, -r, r, r, pat, hollow, (x, y, z) -> x * x + y * y + z * z <= R2);
            case "dome": return build(es, o, r, 0, r, r, pat, hollow, (x, y, z) -> y >= 0 && x * x + y * y + z * z <= R2);
            case "bowl": return build(es, o, r, -r, 0, r, pat, hollow, (x, y, z) -> y <= 0 && x * x + y * y + z * z <= R2);
            case "ellipsoid": {
                double a = r + .5, b = h / 2.0 + .5, c = r2 + .5;
                return build(es, o, r, -h / 2, h / 2, r2, pat, hollow, (x, y, z) -> (x * x) / (a * a) + (y * y) / (b * b) + (z * z) / (c * c) <= 1);
            }
            case "cylinder": return build(es, o, r, 0, h - 1, r, pat, hollow, (x, y, z) -> y >= 0 && y < h && x * x + z * z <= R2);
            case "disc": return build(es, o, r, 0, 0, r, pat, hollow, (x, y, z) -> y == 0 && x * x + z * z <= R2);
            case "cone": return build(es, o, r, 0, h - 1, r, pat, hollow, (x, y, z) -> { if (y < 0 || y >= h) return false; double rr = (r + .5) * (1 - (double) y / h); return x * x + z * z <= rr * rr; });
            case "pyramid": return build(es, o, h, 0, h - 1, h, pat, hollow, (x, y, z) -> y >= 0 && y < h && Math.abs(x) <= h - 1 - y && Math.abs(z) <= h - 1 - y);
            case "cube": return build(es, o, r, -r, r, r, pat, hollow, (x, y, z) -> Math.abs(x) <= r && Math.abs(y) <= r && Math.abs(z) <= r);
            case "torus": {
                double minor = Math.max(1, r2) + .5;
                return build(es, o, r + r2, -r2, r2, r + r2, pat, hollow, (x, y, z) -> { double q = Math.sqrt(x * x + z * z) - r; return q * q + y * y <= minor * minor; });
            }
            case "arch": {
                // a half-ring standing up, `r2` thick, `h` deep (along z)
                double out = R, inn = Math.max(0, r - Math.max(1, r2)) + .5;
                return build(es, o, r, 0, r, h / 2, pat, false, (x, y, z) -> { double d = x * x + y * y; return y >= 0 && Math.abs(z) <= h / 2 && d <= out * out && d > inn * inn; });
            }
            case "helix": {
                int n = 0, steps = Math.max(1, turns) * 64;
                for (int i = 0; i <= steps; i++) {
                    double t = (double) i / steps, ang = t * Math.max(1, turns) * Math.PI * 2;
                    BlockPos c = o.add(Math.round(Math.cos(ang) * r), Math.round(t * (h - 1)), Math.round(Math.sin(ang) * r));
                    int th = Math.max(0, r2 - 1);
                    for (int dx = -th; dx <= th; dx++) for (int dy = -th; dy <= th; dy++) for (int dz = -th; dz <= th; dz++)
                        if (dx * dx + dy * dy + dz * dz <= th * th) { int before = es.size(); if (!es.set(c.add(dx, dy, dz), pat.next())) return n; if (es.size() > before) n++; }
                }
                return n;
            }
            default: throw new IllegalArgumentException("Unknown shape: " + shape);
        }
    }
}
