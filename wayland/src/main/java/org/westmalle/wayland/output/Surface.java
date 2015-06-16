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
package org.westmalle.wayland.output;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.Lists;

import org.freedesktop.wayland.server.ShmBuffer;
import org.freedesktop.wayland.server.WlBufferResource;
import org.freedesktop.wayland.server.WlCallbackResource;
import org.freedesktop.wayland.server.WlCompositorResource;
import org.freedesktop.wayland.server.WlRegionResource;
import org.westmalle.wayland.output.calc.Mat4;
import org.westmalle.wayland.output.calc.Vec4;
import org.westmalle.wayland.protocol.WlCompositor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

@AutoFactory(className = "SurfaceFactory")
public class Surface {

    @Nonnull
    private final FiniteRegionFactory  finiteRegionFactory;
    @Nonnull
    private final WlCompositorResource wlCompositorResource;

    //pending state
    @Nonnull
    private       SurfaceState pendingState  = SurfaceState.builder()
                                                           .build();

    //committed state
    @Nonnull
    private SurfaceState state = SurfaceState.builder()
                                             .build();
    //committed derived states
    private boolean destroyed;
    @Nonnull
    private Mat4      transform           = Transforms.NORMAL;
    @Nonnull
    private Mat4      inverseTransform    = Transforms.NORMAL;
    @Nonnull
    private Rectangle size                = Rectangle.ZERO;

    @Nonnull
    private final List<WlCallbackResource> callbacks = Lists.newLinkedList();

    Surface(@Nonnull @Provided final FiniteRegionFactory finiteRegionFactory,
            @Nonnull final WlCompositorResource wlCompositorResource) {
        this.finiteRegionFactory = finiteRegionFactory;
        this.wlCompositorResource = wlCompositorResource;
    }

    @Nonnull
    public List<WlCallbackResource> getFrameCallbacks() {
        return this.callbacks;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    @Nonnull
    public Mat4 getTransform() {
        return this.transform;
    }

    @Nonnull
    public Surface markDestroyed() {
        this.destroyed = true;
        return this;
    }

    @Nonnull
    public Surface markDamaged(@Nonnull final Rectangle damage) {
        final Region newDamage = getPendingState().getDamage()
                                                  .orElse(this.finiteRegionFactory.create())
                                                  .add(damage);
        this.pendingState = getPendingState().toBuilder()
                                             .damage(Optional.of(newDamage))
                                             .build();
        return this;
    }

    @Nonnull
    public Surface attachBuffer(@Nonnull final WlBufferResource buffer,
                                final int dx,
                                final int dy) {
        this.pendingState = getPendingState().toBuilder()
                                             .buffer(Optional.of(buffer))
                                             .positionTransform(Transforms.TRANSLATE(dx,
                                                                                     dy)
                                                                          .multiply(getState().getPositionTransform()))
                                             .build();
        return this;
    }

    @Nonnull
    public SurfaceState getPendingState() {
        return this.pendingState;
    }

    @Nonnull
    public Surface detachBuffer() {
        this.pendingState = getPendingState().toBuilder()
                                             .buffer(Optional.<WlBufferResource>empty())
                                             .damage(Optional.<Region>empty())
                                             .build();
        return this;
    }

    @Nonnull
    public Surface commit() {
        final Optional<WlBufferResource> buffer = getState().getBuffer();
        if (buffer.isPresent()) {
            //signal client that the previous buffer can be reused as we will now use the
            //newly attached buffer.
            buffer.get()
                  .release();
        }
        //check update transformation
        final boolean needsTransformUpdate = needsTransformUpdate();
        //flush states
        this.state = this.pendingState;
        if (needsTransformUpdate) {
            updateTransform();
        }
        updateSize();
        //reset pending buffer state
        detachBuffer();
        final WlCompositor wlCompositor = (WlCompositor) this.wlCompositorResource.getImplementation();
        wlCompositor.getCompositor()
                    .requestRender();
        return this;
    }

    public void updateSize() {
        final SurfaceState state = getState();
        final Optional<WlBufferResource> buffer = state.getBuffer();
        final int scale = state.getScale();
        if (buffer.isPresent()) {
            final WlBufferResource wlBufferResource = buffer.get();
            //FIXME we shouldn't assume the buffer to always be an shm buffer.
            final ShmBuffer shmBuffer = ShmBuffer.get(wlBufferResource);
            final int width = shmBuffer.getWidth() / scale;
            final int height = shmBuffer.getHeight() / scale;
            this.size = Rectangle.builder()
                                 .width(width)
                                 .height(height)
                                 .build();
        }
        else {
            this.size = Rectangle.ZERO;
        }
    }

    @Nonnull
    public SurfaceState getState() {
        return this.state;
    }

    public boolean needsTransformUpdate() {
        final SurfaceState pendingState = getPendingState();
        final SurfaceState state = getState();
        return pendingState.getScale() != state.getScale()
               || !pendingState.getBufferTransform()
                                    .equals(state.getBufferTransform())
               || !pendingState.getPositionTransform()
                                    .equals(state.getPositionTransform());
    }

    @Nonnull
    public Surface updateTransform() {
        final SurfaceState state = getState();

        //set scaling first
        Mat4 result = Transforms.SCALE(state.getScale());
        //apply positioning
        result = state.getPositionTransform().multiply(result);
        //client buffer transform;
        result = state.getBufferTransform().multiply(result);
        //homogenized
        result = Transforms.SCALE(1f/result.getM33()).multiply(result);

        this.transform = result;
        this.inverseTransform = getTransform().invert();
        return this;
    }

    @Nonnull
    public Surface addCallback(@Nonnull final WlCallbackResource callback) {
        this.callbacks.add(callback);
        return this;
    }

    @Nonnull
    public Surface removeOpaqueRegion() {
        this.pendingState = this.pendingState.toBuilder()
                                             .opaqueRegion(Optional.<WlRegionResource>empty())
                                             .build();
        return this;
    }

    @Nonnull
    public Surface setOpaqueRegion(@Nonnull final WlRegionResource opaqueRegion) {
        this.pendingState = this.pendingState.toBuilder()
                                             .opaqueRegion(Optional.of(opaqueRegion))
                                             .build();
        return this;
    }

    @Nonnull
    public Surface removeInputRegion() {
        this.pendingState = this.pendingState.toBuilder()
                                             .inputRegion(Optional.<WlRegionResource>empty())
                                             .build();
        return this;
    }

    @Nonnull
    public Surface setInputRegion(@Nonnull final WlRegionResource inputRegion) {
        this.pendingState = this.pendingState.toBuilder()
                                             .inputRegion(Optional.of(inputRegion))
                                             .build();
        return this;
    }

    @Nonnull
    public Surface setPosition(@Nonnull final Point global) {
        final SurfaceState state = getState();
        final int scale = state.getScale();
        this.state = state.toBuilder()
                          .positionTransform(Transforms.TRANSLATE(global.getX()*scale,
                                                                  global.getY()*scale))
                          .build();
        updateTransform();
        final WlCompositor wlCompositor = (WlCompositor) this.wlCompositorResource.getImplementation();
        wlCompositor.getCompositor()
                    .requestRender();
        return this;
    }

    @Nonnull
    public Surface firePaintCallbacks(final int serial) {
        final List<WlCallbackResource> callbacks = new ArrayList<>(getFrameCallbacks());
        getFrameCallbacks().clear();
        callbacks.forEach(frameCallback -> {
            frameCallback.done(serial);
            frameCallback.destroy();
        });
        return this;
    }

    @Nonnull
    public Point local(@Nonnull final Point global) {
        //TODO unit test this method
        final Vec4 localPoint = getInverseTransform().multiply(global.toVec4());
        return Point.create((int)localPoint.getX(),
                            (int)localPoint.getY());
    }

    @Nonnull
    public Point global(@Nonnull final Point local) {
        //TODO unit test this method
        final Vec4 localPoint = getTransform().multiply(local.toVec4());
        return Point.create(Math.round(localPoint.getX()),
                            Math.round(localPoint.getY()));
    }

    @Nonnull
    public Rectangle getSize() {
        return this.size;
    }

    public Surface setScale(@Nonnegative final int scale) {
        this.pendingState = this.pendingState.toBuilder()
                                             .scale(scale)
                                             .build();
        return this;
    }

    @Nonnull
    public Surface setBufferTransform(@Nonnull final Mat4 bufferTransform) {
        this.pendingState = this.pendingState.toBuilder()
                                             .bufferTransform(bufferTransform)
                                             .build();
        return this;
    }

    @Nonnull
    public Mat4 getInverseTransform() {
        return this.inverseTransform;
    }
}