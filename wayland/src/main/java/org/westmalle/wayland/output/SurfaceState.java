package org.westmalle.wayland.output;

import com.google.auto.value.AutoValue;
import com.hackoeur.jglm.Mat4;
import org.freedesktop.wayland.server.WlBufferResource;
import org.freedesktop.wayland.server.WlRegionResource;

import javax.annotation.Nonnegative;
import java.util.Optional;

@AutoValue
public abstract class SurfaceState {

    static Builder builder() {
        return new AutoValue_SurfaceState.Builder().opaqueRegion(Optional.<WlRegionResource>empty())
                                                   .inputRegion(Optional.<WlRegionResource>empty())
                                                   .damage(Optional.<Region>empty())
                                                   .buffer(Optional.<WlBufferResource>empty())
                                                   .bufferTransform(Mat4.MAT4_IDENTITY)
                                                   .scale(1);
    }

    public abstract Optional<WlRegionResource> getOpaqueRegion();

    public abstract Optional<WlRegionResource> getInputRegion();

    public abstract Optional<Region> getDamage();

    public abstract Optional<WlBufferResource> getBuffer();

    public abstract Mat4 getBufferTransform();

    @Nonnegative
    abstract int getScale();

    @AutoValue.Builder
    interface Builder {
        Builder opaqueRegion(Optional<WlRegionResource> wlRegionResource);

        Builder inputRegion(Optional<WlRegionResource> wlRegionResource);

        Builder damage(Optional<Region> damage);

        Builder buffer(Optional<WlBufferResource> wlBufferResource);

        Builder bufferTransform(Mat4 bufferTransform);

        Builder scale(@Nonnegative int scale);

        SurfaceState build();
    }

    abstract Builder toBuilder();
}