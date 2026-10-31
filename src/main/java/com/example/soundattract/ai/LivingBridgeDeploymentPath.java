package com.example.soundattract.ai;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A bounded, immutable deployment trajectory. Clearance corrections are movement, not exemptions. */
final class LivingBridgeDeploymentPath {
    interface Space {
        Vec3 resolve(int slot, Vec3 desired);
        boolean sweep(int slot, Vec3 from, Vec3 to);
    }

    record Result(LivingBridgeDeploymentPath path, int blockedSlot, Vec3 blockedPosition, String detail) {
        Result(LivingBridgeDeploymentPath path, int slot, Vec3 position) { this(path, slot, position, ""); }
    }
    private final List<List<Vec3>> frames;
    private LivingBridgeDeploymentPath(List<List<Vec3>> frames) { this.frames = List.copyOf(frames); }
    int steps() { return frames.size() - 1; }
    Vec3 position(int slot, double progress) {
        double frame = Mth.clamp(progress, 0.0, 1.0) * steps();
        int lower = Math.min(steps(), (int) frame);
        return frames.get(lower).get(slot).lerp(frames.get(Math.min(steps(), lower + 1)).get(slot), frame - lower);
    }

    static boolean connected(Vec3 a, Vec3 b, double width, double height) {

        return Math.abs(a.x - b.x) <= width + 0.08
                && Math.abs(a.z - b.z) <= width + 0.08
                && Math.abs(a.y - b.y) <= height + 0.08;
    }

    static Result solve(List<Vec3> sources, List<Vec3> destinations, int columns,
                        double width, double height, int minimumSteps, Space space) {
        double distance = 0.0;
        for (int slot = 0; slot < sources.size(); slot++) {
            distance = Math.max(distance, sources.get(slot).distanceTo(destinations.get(slot)));
        }
        int steps = Math.max(minimumSteps, Math.max(20, Mth.ceil(distance / 0.08)));
        if (steps > 512) return new Result(null, 0, destinations.get(0));


        Vec3[][] targets = new Vec3[steps + 1][sources.size()];
        double[][] heights = new double[steps + 1][sources.size()];
        for (int frame = 0; frame <= steps; frame++) for (int slot = 0; slot < sources.size(); slot++) {
            Vec3 desired = slot < columns ? sources.get(slot)
                    : sources.get(slot).lerp(destinations.get(slot), frame / (double) steps);
            Vec3 resolved = frame == 0 ? desired : space.resolve(slot, desired);
            targets[frame][slot] = resolved == null ? desired : resolved;
            heights[frame][slot] = targets[frame][slot].y;
        }
        for (int pass = 0; pass <= sources.size(); pass++) {
            boolean changed = false;
            for (int frame = 0; frame <= steps; frame++) {
                for (int slot = sources.size() - 1; slot >= columns; slot--) {
                    double needed = heights[frame][slot] - height - 0.06;
                    if (needed > heights[frame][slot - columns] + 1.0E-8) {
                        heights[frame][slot - columns] = needed; changed = true;
                    }
                }
                for (int slot = columns; slot < sources.size(); slot++) {
                    double needed = heights[frame][slot - columns] - height - 0.06;
                    if (needed > heights[frame][slot] + 1.0E-8) {
                        heights[frame][slot] = needed; changed = true;
                    }
                }
            }
            for (int slot = 0; slot < sources.size(); slot++) {
                for (int frame = steps - 1; frame >= 0; frame--) {
                    double needed = heights[frame + 1][slot] - 0.12;
                    if (needed > heights[frame][slot] + 1.0E-8) { heights[frame][slot] = needed; changed = true; }
                }
                for (int frame = 1; frame <= steps; frame++) {
                    double needed = heights[frame - 1][slot] - 0.12;
                    if (needed > heights[frame][slot] + 1.0E-8) { heights[frame][slot] = needed; changed = true; }
                }
                if (heights[0][slot] > sources.get(slot).y + 1.0E-6)
                    return new Result(null, slot, sources.get(slot));
            }
            for (int frame = 0; frame <= steps; frame++) for (int slot = 0; slot < columns; slot++)
                if (heights[frame][slot] > sources.get(slot).y + 1.0E-6)
                    return new Result(null, slot, targets[frame][slot]);
            if (!changed) break;
        }
        List<List<Vec3>> frames = new ArrayList<>(steps + 1);
        for (int slot = 0; slot < sources.size(); slot++) {
            if (!space.sweep(slot, sources.get(slot), sources.get(slot)))
                return new Result(null, slot, sources.get(slot));
        }
        frames.add(List.copyOf(sources));
        boolean settled = false;
        for (int frame = 1; frame <= steps + 32; frame++) {
            List<Vec3> previous = frames.get(frame - 1);
            List<Vec3> next = new ArrayList<>(sources.size());
            int targetFrame = Math.min(frame, steps);
            for (int slot = 0; slot < sources.size(); slot++) {
                Vec3 target = targets[targetFrame][slot];
                Vec3 desired = new Vec3(target.x, heights[targetFrame][slot], target.z);
                Vec3 parent = slot >= columns ? next.get(slot - columns) : null;
                Vec3 selected;
                if (slot < columns) {
                    selected = sources.get(slot);
                    if (!space.sweep(slot, previous.get(slot), selected)) selected = null;
                } else {
                    selected = candidate(space, slot, desired, previous.get(slot), parent, width, height);
                    if (selected == null && desired.y < previous.get(slot).y) {


                        selected = candidate(space, slot, new Vec3(desired.x, previous.get(slot).y, desired.z),
                                previous.get(slot), parent, width, height);
                    }
                    if (selected == null) {


                        double best = Double.POSITIVE_INFINITY;
                        for (int radius = 1; radius <= 8; radius++) {
                            double offset = radius * 0.25;
                            for (Vec3 delta : List.of(new Vec3(offset, 0, 0), new Vec3(-offset, 0, 0),
                                    new Vec3(0, 0, offset), new Vec3(0, 0, -offset), new Vec3(0, offset, 0))) {
                                Vec3 trial = candidate(space, slot, desired.add(delta), previous.get(slot), parent, width, height);
                                if (trial == null) continue;
                                double score = trial.distanceToSqr(desired) + 0.1 * trial.distanceToSqr(previous.get(slot));
                                if (score < best) { best = score; selected = trial; }
                            }
                            if (selected != null) break;
                        }
                    }
                }
                if (selected == null) return new Result(null, slot, desired,
                        "frame=" + frame + "/" + steps + " previous=" + previous.get(slot) + " parent=" + parent
                                + " resolved=" + space.resolve(slot, desired));

                if (frame == steps + 32 && slot >= sources.size() - columns) {
                    Vec3 landing = space.resolve(slot, destinations.get(slot));
                    if (landing == null || selected.distanceToSqr(landing) > 1.0E-6)
                        return new Result(null, slot, destinations.get(slot),
                                "landing_not_reached selected=" + selected + " landing=" + landing
                                        + " target=" + desired + " parent=" + parent + " previous=" + previous.get(slot));
                }
                next.add(selected);
            }
            frames.add(List.copyOf(next));
            if (frame >= steps) {
                settled = true;
                for (int slot = 0; slot < sources.size(); slot++) {
                    Vec3 end = new Vec3(targets[steps][slot].x, heights[steps][slot], targets[steps][slot].z);
                    if (next.get(slot).distanceToSqr(end) > 1.0E-6) settled = false;
                    if (slot >= sources.size() - columns) {
                        Vec3 landing = space.resolve(slot, destinations.get(slot));
                        if (landing == null || next.get(slot).distanceToSqr(landing) > 1.0E-6) settled = false;
                    }
                }
                if (settled) break;
            }
        }
        if (!settled) return new Result(null, sources.size() - 1, destinations.getLast(), "detour_did_not_converge");
        return new Result(new LivingBridgeDeploymentPath(frames), -1, null);
    }

    private static Vec3 candidate(Space space, int slot, Vec3 desired, Vec3 previous, Vec3 parent,
                                   double width, double height) {
        desired = contactTarget(desired, parent, width, height);
        if (previous.distanceToSqr(desired) > 0.20 * 0.20) {
            desired = previous.add(desired.subtract(previous).normalize().scale(0.20));
        }


        desired = contactTarget(desired, parent, width, height);
        Vec3 resolved = space.resolve(slot, desired);
        if (resolved == null || previous.distanceToSqr(resolved) > 0.25 * 0.25
                || (parent != null && !connected(parent, resolved, width, height))
                || !space.sweep(slot, previous, resolved)) return null;
        return resolved;
    }

    private static Vec3 contactTarget(Vec3 desired, Vec3 parent, double width, double height) {
        if (parent == null) return desired;
        double horizontal = width + 0.08 - 1.0E-7;
        double vertical = height + 0.08 - 1.0E-7;
        return new Vec3(Mth.clamp(desired.x, parent.x - horizontal, parent.x + horizontal),
                Mth.clamp(desired.y, parent.y - vertical, parent.y + vertical),
                Mth.clamp(desired.z, parent.z - horizontal, parent.z + horizontal));
    }

}
