package io.github.nishiki001.rttc.world.classic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code rttc:beta_terrain}: Beta-1.7.3-style 3D terrain density, scaled to the −64..320 world.
 *
 * <p>Like Beta, it lerps between a "min limit" and a "max limit" 3D noise, picked by a sharp "main" selector noise,
 * then subtracts a vertical falloff around a surface height (stronger below the surface, as in Beta, so overhangs
 * and floating bits stay rare). The surface height comes from the 2D {@code continents} input; the {@code river}
 * input pulls it down to {@code river_floor_y} where {@code |river| < river_width}. The same inputs drive
 * {@link ClassicBiomeSource}, so coasts and rivers line up with their biomes.
 *
 * <p>Wrap it in {@code minecraft:interpolated} in JSON: it is then evaluated at 4×8×4 cell corners, Beta's grid.
 * With {@code surface_estimate: true} it skips the 3D noise and returns a smooth estimate; that variant is meant
 * for the router's {@code initial_density_without_jaggedness} (preliminary surface for aquifers/surface rules).
 * Beta-style, not seed-exact Beta: the noises are vanilla {@code NormalNoise}s seeded per world.
 */
public record ClassicTerrain(
        NoiseHolder minLimit, NoiseHolder maxLimit, NoiseHolder main,
        DensityFunction continents, DensityFunction river, Shape shape, boolean surfaceEstimate)
        implements DensityFunction {

    /** Tunable numbers, all in blocks unless noted. */
    public record Shape(
            double baseY, double heightScale, double relief, double belowSurfaceFactor,
            double noiseAmplitude, double selectorSharpness, double xzScale, double yScale,
            double mainXzScale, double mainYScale, double riverWidth, double riverFloorY,
            int minY, int maxY) {
        static final Codec<Shape> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("base_y").forGetter(Shape::baseY),
                Codec.DOUBLE.fieldOf("height_scale").forGetter(Shape::heightScale),
                Codec.doubleRange(0.1, 1000).fieldOf("relief").forGetter(Shape::relief),
                Codec.DOUBLE.optionalFieldOf("below_surface_factor", 4.0).forGetter(Shape::belowSurfaceFactor),
                Codec.DOUBLE.fieldOf("noise_amplitude").forGetter(Shape::noiseAmplitude),
                Codec.DOUBLE.fieldOf("selector_sharpness").forGetter(Shape::selectorSharpness),
                Codec.DOUBLE.fieldOf("xz_scale").forGetter(Shape::xzScale),
                Codec.DOUBLE.fieldOf("y_scale").forGetter(Shape::yScale),
                Codec.DOUBLE.fieldOf("main_xz_scale").forGetter(Shape::mainXzScale),
                Codec.DOUBLE.fieldOf("main_y_scale").forGetter(Shape::mainYScale),
                Codec.DOUBLE.fieldOf("river_width").forGetter(Shape::riverWidth),
                Codec.DOUBLE.fieldOf("river_floor_y").forGetter(Shape::riverFloorY),
                Codec.INT.fieldOf("min_y").forGetter(Shape::minY),
                Codec.INT.fieldOf("max_y").forGetter(Shape::maxY)
        ).apply(i, Shape::new));
    }

    private static final MapCodec<ClassicTerrain> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NoiseHolder.CODEC.fieldOf("min_limit_noise").forGetter(ClassicTerrain::minLimit),
            NoiseHolder.CODEC.fieldOf("max_limit_noise").forGetter(ClassicTerrain::maxLimit),
            NoiseHolder.CODEC.fieldOf("main_noise").forGetter(ClassicTerrain::main),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(ClassicTerrain::continents),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("river").forGetter(ClassicTerrain::river),
            Shape.CODEC.fieldOf("shape").forGetter(ClassicTerrain::shape),
            Codec.BOOL.optionalFieldOf("surface_estimate", false).forGetter(ClassicTerrain::surfaceEstimate)
    ).apply(i, ClassicTerrain::new));
    public static final KeyDispatchDataCodec<ClassicTerrain> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    /** Bottom few blocks are forced solid so the bedrock floor never shows void. */
    private static final int FLOOR_SOLID = 6;
    /** Top band slides to air so nothing touches the build limit. */
    private static final int TOP_SLIDE = 24;
    private static final double BOUND = 1000.0;

    @Override
    public double compute(FunctionContext ctx) {
        Shape s = this.shape;
        int x = ctx.blockX(), y = ctx.blockY(), z = ctx.blockZ();
        if (y <= s.minY() + FLOOR_SOLID) {
            return BOUND / 10;
        }
        double surface = surfaceY(ctx);
        double falloff = (y - surface) / s.relief();
        if (falloff < 0) {
            falloff *= s.belowSurfaceFactor();
        }
        double density;
        if (this.surfaceEstimate) {
            density = -falloff;
        } else {
            double lo = this.minLimit.getValue(x * s.xzScale(), y * s.yScale(), z * s.xzScale());
            double hi = this.maxLimit.getValue(x * s.xzScale(), y * s.yScale(), z * s.xzScale());
            double sel = this.main.getValue(x * s.mainXzScale(), y * s.mainYScale(), z * s.mainXzScale());
            double t = Mth.clamp(0.5 + sel * s.selectorSharpness(), 0.0, 1.0);
            density = Mth.lerp(t, lo, hi) * s.noiseAmplitude() - falloff;
        }
        int topStart = s.maxY() - TOP_SLIDE;
        if (y > topStart) {
            density = Mth.lerp((y - topStart) / (double) TOP_SLIDE, density, -1.0);
        }
        return Mth.clamp(density, -BOUND, BOUND);
    }

    /** Surface height from continents, pulled down to the river floor inside river channels. */
    private double surfaceY(FunctionContext ctx) {
        Shape s = this.shape;
        double surface = s.baseY() + this.continents.compute(ctx) * s.heightScale();
        double r = Math.abs(this.river.compute(ctx));
        if (r < s.riverWidth() && surface > s.riverFloorY()) {
            double k = 1.0 - r / s.riverWidth(); // 1 at the river centre, 0 at its edge
            surface = Mth.lerp(k * k * (3 - 2 * k), surface, s.riverFloorY()); // smoothstep banks
        }
        return surface;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ClassicTerrain(
                visitor.visitNoise(this.minLimit), visitor.visitNoise(this.maxLimit), visitor.visitNoise(this.main),
                this.continents.mapAll(visitor), this.river.mapAll(visitor), this.shape, this.surfaceEstimate));
    }

    @Override
    public double minValue() {
        return -BOUND;
    }

    @Override
    public double maxValue() {
        return BOUND;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
