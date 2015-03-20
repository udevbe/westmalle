//Copyright 2015 Erik De Rijcke
//
//Licensed under the Apache License,Version2.0(the"License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,software
//distributed under the License is distributed on an"AS IS"BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
package org.westmalle.wayland.protocol;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.hackoeur.jglm.Mat4;
import org.freedesktop.wayland.server.*;
import org.freedesktop.wayland.shared.WlOutputTransform;
import org.freedesktop.wayland.shared.WlSurfaceError;
import org.westmalle.wayland.output.Rectangle;
import org.westmalle.wayland.output.Surface;
import org.westmalle.wayland.output.Transforms;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.hackoeur.jglm.Mat4.MAT4_IDENTITY;

@AutoFactory(className = "WlSurfaceFactory")
public class WlSurface extends EventBus implements WlSurfaceRequestsV3, ProtocolObject<WlSurfaceResource> {

    private final Set<WlSurfaceResource> resources       = Sets.newSetFromMap(new WeakHashMap<>());
    private       Optional<Listener>     destroyListener = Optional.empty();

    private final WlCallbackFactory wlCallbackFactory;
    private final Surface           surface;

    WlSurface(@Provided final WlCallbackFactory wlCallbackFactory,
              final Surface surface) {
        this.wlCallbackFactory = wlCallbackFactory;
        this.surface = surface;
    }

    public Surface getSurface() {
        return this.surface;
    }

    @Override
    public void setBufferScale(final WlSurfaceResource resource,
                               @Nonnegative final int scale) {
        if (scale > 0) {
            getSurface().setScale(scale);
        }
        else {
            resource.postError(WlSurfaceError.INVALID_SCALE.getValue(),
                               String.format("Invalid scale %d. Scale must be positive integer.",
                                             scale));
        }
    }

    @Override
    public void setBufferTransform(final WlSurfaceResource resource,
                                   final int transform) {
        this.surface.setBufferTransform(getMatrix(resource,
                                                  transform));
    }

    private Mat4 getMatrix(final WlSurfaceResource resource,
                           final int transform) {
        if (WlOutputTransform.NORMAL.getValue() == transform) {
            return Transforms.NORMAL;
        }
        else if (WlOutputTransform._90.getValue() == transform) {
            return Transforms._90;
        }
        else if (WlOutputTransform._180.getValue() == transform) {
            return Transforms._180;
        }
        else if (WlOutputTransform._270.getValue() == transform) {
            return Transforms._270;
        }
        else if (WlOutputTransform.FLIPPED.getValue() == transform) {
            return Transforms.FLIPPED;
        }
        else if (WlOutputTransform.FLIPPED_90.getValue() == transform) {
            return Transforms.FLIPPED_90;
        }
        else if (WlOutputTransform.FLIPPED_180.getValue() == transform) {
            return Transforms.FLIPPED_180;
        }
        else if (WlOutputTransform.FLIPPED_270.getValue() == transform) {
            return Transforms.FLIPPED_270;
        }
        else {
            resource.postError(WlSurfaceError.INVALID_TRANSFORM.getValue(),
                               String.format("Invalid transform %d. Supported values are %s.",
                                             transform,
                                             Arrays.asList(WlOutputTransform.values())));
            return MAT4_IDENTITY;
        }
    }

    @Nonnull
    @Override
    public Set<WlSurfaceResource> getResources() {
        return this.resources;
    }

    @Nonnull
    @Override
    public WlSurfaceResource create(@Nonnull final Client client,
                                    @Nonnegative final int version,
                                    final int id) {
        return new WlSurfaceResource(client,
                                     version,
                                     id,
                                     this);
    }


    @Override
    public void destroy(final WlSurfaceResource resource) {
        resource.destroy();
        getSurface().markDestroyed();
    }

    @Override
    public void attach(final WlSurfaceResource requester,
                       @Nullable final WlBufferResource buffer,
                       final int x,
                       final int y) {
        if (buffer == null) {
            detachBuffer();
        }
        else {
            attachBuffer(buffer,
                         x,
                         y);
        }
    }

    @Override
    public void damage(final WlSurfaceResource resource,
                       final int x,
                       final int y,
                       @Nonnegative final int width,
                       @Nonnegative final int height) {
        checkArgument(width > 0);
        checkArgument(height > 0);

        getSurface().markDamaged(Rectangle.create(x,
                                                  y,
                                                  width,
                                                  height));
    }

    @Override
    public void frame(final WlSurfaceResource resource,
                      final int callbackId) {
        final WlCallbackResource callbackResource = this.wlCallbackFactory.create()
                                                                          .add(resource.getClient(),
                                                                               resource.getVersion(),
                                                                               callbackId);
        getSurface().addCallback(callbackResource);
    }

    @Override
    public void setOpaqueRegion(final WlSurfaceResource requester,
                                final WlRegionResource region) {
        if (region == null) {
            getSurface().removeOpaqueRegion();
        }
        else {
            getSurface().setOpaqueRegion(region);
        }
    }

    @Override
    public void setInputRegion(final WlSurfaceResource requester,
                               @Nullable final WlRegionResource regionResource) {
        if (regionResource == null) {
            getSurface().removeInputRegion();
        }
        else {
            getSurface().setInputRegion(regionResource);
        }
    }

    @Override
    public void commit(final WlSurfaceResource requester) {
        removeBufferDestroyListener();
        getSurface().commit();
    }

    private void detachBuffer() {
        removeBufferDestroyListener();
        getSurface().detachBuffer();
    }

    private void addBufferDestroyListener(final WlBufferResource buffer){
        final Listener listener = new Listener() {
            @Override
            public void handle() {
                remove();
                WlSurface.this.detachBuffer();
            }
        };
        this.destroyListener = Optional.of(listener);
        buffer.addDestroyListener(listener);
    }

    private void removeBufferDestroyListener(){
        this.destroyListener.ifPresent(Listener::remove);
        this.destroyListener = Optional.empty();
    }

    private void attachBuffer(final WlBufferResource buffer,
                              final int x,
                              final int y) {

        removeBufferDestroyListener();
        addBufferDestroyListener(buffer);

        getSurface().attachBuffer(buffer,
                                  x,
                                  y);
    }
}
