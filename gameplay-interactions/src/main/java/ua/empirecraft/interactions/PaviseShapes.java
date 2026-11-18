package ua.empirecraft.interactions;

import com.magistuarmory.block.PaviseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PaviseShapes {
    private static final double MODEL_TILT = Math.PI / 8;
    private static final VoxelShape[] LOWER = new VoxelShape[16];
    private static final VoxelShape[] UPPER = new VoxelShape[16];

    static {
        for (int rotation = 0; rotation < LOWER.length; rotation++) {
            double yaw = rotation * Math.PI / 8;
            // The main board, its raised central rib and the two support legs.
            VoxelShape lower = Shapes.empty();
            VoxelShape upper = Shapes.empty();
            for (double[] part : new double[][] {
                {-15.5, -21, -4, 15.5, 11, -3, -MODEL_TILT},
                {-2.5, -21, -5, 2.5, 11, -4, -MODEL_TILT},
                {-8, -8, 3, -6, 8, 4, MODEL_TILT},
                {6, -8, 3, 8, 8, 4, MODEL_TILT}
            }) {
                for (double x = part[0]; x < part[3]; x += 3) {
                    for (double y = part[1]; y < part[4]; y += 2) {
                        double[] box = section(x, y, Math.min(x + 3, part[3]), Math.min(y + 2, part[4]), part, yaw);
                        lower = include(lower, box, 0);
                        upper = include(upper, box, 1);
                    }
                }
            }
            LOWER[rotation] = lower.optimize();
            UPPER[rotation] = upper.optimize();
        }
    }

    private PaviseShapes() {}

    public static VoxelShape lower(BlockState state) {
        return LOWER[state.getValue(PaviseBlock.ROTATION)];
    }

    public static VoxelShape upper(BlockState lower) {
        return lower.getBlock() instanceof PaviseBlock ? UPPER[lower.getValue(PaviseBlock.ROTATION)] : Shapes.empty();
    }

    private static double[] section(double x0, double y0, double x1, double y1, double[] part, double yaw) {
        double sinPitch = Math.sin(part[6]), cosPitch = Math.cos(part[6]);
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double x : new double[]{x0, x1}) {
            for (double y : new double[]{y0, y1}) {
                for (double z : new double[]{part[2], part[5]}) {
                    double depth = -(sinPitch * y + cosPitch * z);
                    double worldX = .5 + (cosYaw * x - sinYaw * depth) / 16;
                    double worldY = .5 - (2 + cosPitch * y - sinPitch * z) / 16;
                    double worldZ = .5 + (sinYaw * x + cosYaw * depth) / 16;
                    bounds[0] = Math.min(bounds[0], worldX);
                    bounds[1] = Math.min(bounds[1], worldY);
                    bounds[2] = Math.min(bounds[2], worldZ);
                    bounds[3] = Math.max(bounds[3], worldX);
                    bounds[4] = Math.max(bounds[4], worldY);
                    bounds[5] = Math.max(bounds[5], worldZ);
                }
            }
        }
        return bounds;
    }

    private static VoxelShape include(VoxelShape shape, double[] box, int half) {
        double low = Math.max(0, roundDown(box[1] - half));
        double high = Math.min(1, roundUp(box[4] - half));
        if (low >= high) return shape;
        return Shapes.joinUnoptimized(shape, Shapes.box(roundDown(box[0]), low, roundDown(box[2]),
            roundUp(box[3]), high, roundUp(box[5])), BooleanOp.OR);
    }

    private static double roundDown(double coordinate) { return Math.floor(coordinate * 32) / 32; }
    private static double roundUp(double coordinate) { return Math.ceil(coordinate * 32) / 32; }
}
