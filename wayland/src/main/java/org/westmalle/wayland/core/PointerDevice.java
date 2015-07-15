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
package org.westmalle.wayland.core;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.Listener;
import org.freedesktop.wayland.server.WlBufferResource;
import org.freedesktop.wayland.server.WlPointerResource;
import org.freedesktop.wayland.server.WlSurfaceRequests;
import org.freedesktop.wayland.server.WlSurfaceResource;
import org.freedesktop.wayland.shared.WlPointerButtonState;
import org.freedesktop.wayland.util.Fixed;
import org.westmalle.wayland.core.events.Button;
import org.westmalle.wayland.core.events.Motion;
import org.westmalle.wayland.core.events.PointerGrab;
import org.westmalle.wayland.protocol.WlSurface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

@AutoFactory(className = "PointerDeviceFactory")
public class PointerDevice implements Role {
    @Nonnull
    private final EventBus            inputBus       = new EventBus();
    @Nonnull
    private final Set<Integer>        pressedButtons = new HashSet<>();
    @Nonnull
    private Point                       position     = Point.ZERO;

    @Nonnull
    private final Map<Client, Cursor> cursors        = new HashMap<>();
    @Nonnull
    private Optional<Cursor>            activeCursor = Optional.empty();
    @Nonnull
    private final CursorFactory       cursorFactory;

    @Nonnull
    private final InfiniteRegion      infiniteRegion;
    @Nonnull
    private final NullRegion          nullRegion;

    @Nonnull
    private final Compositor          compositor;
    @Nonnull
    private final Display             display;

    @Nonnull
    private Optional<Listener> destroyListener = Optional.empty();
    @Nonnull
    private Optional<WlSurfaceResource> grab         = Optional.empty();
    @Nonnull
    private Optional<WlSurfaceResource> focus        = Optional.empty();



    private int buttonPressSerial;
    private int buttonReleaseSerial;
    private int enterSerial;
    private int leaveSerial;

    @Nonnegative
    private int buttonsPressed;

    PointerDevice(@Provided @Nonnull final Display display,
                  @Provided @Nonnull final InfiniteRegion infiniteRegion,
                  @Provided @Nonnull final NullRegion nullRegion,
                  @Provided @Nonnull final CursorFactory cursorFactory,
                  @Nonnull final Compositor compositor) {
        this.display = display;
        this.infiniteRegion = infiniteRegion;
        this.nullRegion = nullRegion;
        this.cursorFactory = cursorFactory;
        this.compositor = compositor;
    }

    public void motion(final Set<WlPointerResource> pointerResources,
                       final int time,
                       final int x,
                       final int y) {
        doMotion(pointerResources,
                 time,
                 x,
                 y);
        this.inputBus.post(Motion.create(time,
                                         getPosition()));
    }

    public void doMotion(@Nonnull final Set<WlPointerResource> pointerResources,
                         final int time,
                         final int x,
                         final int y) {
        this.position = Point.create(x,
                                     y);
        //TODO update unit tests to reflect changed pointer grab implementation
        if (getGrab().isPresent()) {
            reportMotion(pointerResources,
                         time,
                         getGrab().get());
        }
        else {
            final Optional<WlSurfaceResource> newFocus = over();
            final Optional<WlSurfaceResource> oldFocus = getFocus();
            this.focus = newFocus;

            if (!oldFocus.equals(newFocus)) {
                if (oldFocus.isPresent()) {
                    reportLeave(pointerResources,
                                oldFocus.get());
                }
                if (newFocus.isPresent()) {
                    reportEnter(pointerResources,
                                newFocus.get());
                }
            }
            if (newFocus.isPresent()) {
                reportMotion(pointerResources,
                             time,
                             newFocus.get());
            }
        }
        updateActiveCursor();
    }

    @Nonnull
    public Point getPosition() {
        return this.position;
    }

    @Nonnull
    public Optional<WlSurfaceResource> getGrab() {
        return this.grab;
    }

    private void reportMotion(final Set<WlPointerResource> pointerResources,
                              final int time,
                              final WlSurfaceResource wlSurfaceResource) {
        final Optional<WlPointerResource> pointerResource = findPointerResource(pointerResources,
                                                                                wlSurfaceResource);
        if (pointerResource.isPresent()) {
            final WlSurface wlSurface = (WlSurface) wlSurfaceResource.getImplementation();
            final Point relativePoint = wlSurface.getSurface()
                                                 .local(getPosition());
            pointerResource.get()
                           .motion(time,
                                   Fixed.create(relativePoint.getX()),
                                   Fixed.create(relativePoint.getY()));
        }
    }

    @Nonnull
    public Optional<WlSurfaceResource> over() {
        final Iterator<WlSurfaceResource> surfaceIterator = this.compositor.getSurfacesStack()
                                                                           .descendingIterator();
        Optional<WlSurfaceResource> pointerOver = Optional.empty();
        while (surfaceIterator.hasNext()) {
            final WlSurfaceResource surfaceResource = surfaceIterator.next();
            final WlSurfaceRequests implementation = surfaceResource.getImplementation();
            final Surface surface = ((WlSurface) implementation).getSurface();

            final Optional<Region> inputRegion = surface.getState()
                                                        .getInputRegion();
            final Region region = inputRegion.orElseGet(() -> this.infiniteRegion);

            if (region.contains(surface.getSize(),
                                surface.local(getPosition()))) {
                pointerOver = Optional.of(surfaceResource);
                break;
            }
        }

        return pointerOver;
    }

    @Nonnull
    public Optional<WlSurfaceResource> getFocus() {
        return this.focus;
    }

    private void reportLeave(final Set<WlPointerResource> wlPointer,
                             final WlSurfaceResource wlSurfaceResource) {
        final Optional<WlPointerResource> pointerResource = findPointerResource(wlPointer,
                                                                                wlSurfaceResource);
        if (pointerResource.isPresent()) {
            pointerResource.get()
                           .leave(nextLeaveSerial(),
                                  wlSurfaceResource);
        }
    }

    public void reportEnter(final Set<WlPointerResource> wlPointer,
                            final WlSurfaceResource wlSurfaceResource) {
        final Optional<WlPointerResource> pointerResource = findPointerResource(wlPointer,
                                                                                wlSurfaceResource);
        if (pointerResource.isPresent()) {

            final WlSurface wlSurface = (WlSurface) wlSurfaceResource.getImplementation();
            final Point relativePoint = wlSurface.getSurface()
                                                 .local(getPosition());
            pointerResource.get()
                           .enter(nextEnterSerial(),
                                  wlSurfaceResource,
                                  Fixed.create(relativePoint.getX()),
                                  Fixed.create(relativePoint.getY()));
        }
    }

    private void updateActiveCursor() {

        final Cursor newCursor;
        if (getFocus().isPresent()) {
            newCursor = this.cursors.get(getFocus().get()
                                                   .getClient());
        }
        else {
            newCursor = null;
        }
        final Optional<Cursor> oldCursor = this.activeCursor;
        this.activeCursor = Optional.ofNullable(newCursor);

        this.activeCursor.ifPresent(clientCursor -> clientCursor.updatePosition(getPosition()));

        if (!oldCursor.equals(this.activeCursor)) {
            oldCursor.ifPresent(Cursor::hide);
        }
    }

    private Optional<WlPointerResource> findPointerResource(final Set<WlPointerResource> pointerResources,
                                                            final WlSurfaceResource wlSurfaceResource) {
        for (final WlPointerResource wlPointerResource : pointerResources) {
            if (wlSurfaceResource.getClient()
                                 .equals(wlPointerResource.getClient())) {
                return Optional.of(wlPointerResource);
            }
        }
        return Optional.empty();
    }

    public int nextLeaveSerial() {
        this.leaveSerial = this.display.nextSerial();
        return getLeaveSerial();
    }

    public int nextEnterSerial() {
        this.enterSerial = this.display.nextSerial();
        return getEnterSerial();
    }

    public int getLeaveSerial() {
        return this.leaveSerial;
    }

    public int getEnterSerial() {
        return this.enterSerial;
    }

    public void button(@Nonnull final Set<WlPointerResource> pointerResources,
                       final int time,
                       @Nonnegative final int button,
                       @Nonnull final WlPointerButtonState buttonState) {
        if (buttonState == WlPointerButtonState.PRESSED) {
            this.pressedButtons.add(button);
        }
        else {
            this.pressedButtons.remove(button);
        }
        doButton(pointerResources,
                 time,
                 button,
                 buttonState);
        this.inputBus.post(Button.create(time,
                                         button,
                                         buttonState));
    }

    private void doButton(@Nonnull final Set<WlPointerResource> pointerResources,
                          final int time,
                          @Nonnegative final int button,
                          @Nonnull final WlPointerButtonState buttonState) {
        if (buttonState == WlPointerButtonState.PRESSED) {
            this.buttonsPressed++;
        }
        else if (this.buttonsPressed > 0) {
            //make sure we only decrement if we had a least one increment.
            //Is such a thing even possible? Yes it is. (Aliens pressing a button before starting compositor)
            this.buttonsPressed--;
        }
        if (getGrab().isPresent()) {
            //always report all buttons if we have a grabbed surface.
            reportButton(pointerResources,
                         time,
                         button,
                         buttonState);
        }
        if (this.buttonsPressed == 0) {
            clearGrab();
        }
        else if (!getGrab().isPresent() && this.focus.isPresent()) {
            //no grab, but we do have a focus and a pressed button. Focused surface becomes grab.
            updateGrab();
            reportButton(pointerResources,
                         time,
                         button,
                         buttonState);
        }
    }

    private void updateGrab(){
        //TODO  add a unit test: listen surface destruction and release the grab

        //grab will be updated, don't listen for previous grab surface destruction.
        this.destroyListener.ifPresent(Listener::remove);
        this.grab = getFocus();
        this.destroyListener = Optional.of(new Listener() {
            @Override
            public void handle() {
                remove();
                PointerDevice.this.destroyListener = Optional.empty();
                clearGrab();
            }
        });
        //if the surface having the grab is destroyed, we clear the grab
        getGrab().get().addDestroyListener(this.destroyListener.get());
        this.inputBus.post(PointerGrab.create(getGrab()));
    }

    private void clearGrab(){
        //grab will be updated, don't listen for previous grab surface destruction.
        this.destroyListener.ifPresent(Listener::remove);
        this.grab = Optional.empty();
        this.inputBus.post(PointerGrab.create(getGrab()));
    }

    private void reportButton(@Nonnull final Set<WlPointerResource> pointerResources,
                              final int time,
                              @Nonnegative final int button,
                              @Nonnull final WlPointerButtonState buttonState) {
        final WlSurfaceResource wlSurfaceResource = getGrab().get();
        final Optional<WlPointerResource> pointerResource = findPointerResource(pointerResources,
                                                                                wlSurfaceResource);
        if (pointerResource.isPresent()) {
            pointerResource.get()
                           .button(buttonState == WlPointerButtonState.PRESSED ?
                                   nextButtonPressSerial() : nextButtonReleaseSerial(),
                                   time,
                                   button,
                                   buttonState.getValue());
        }
    }

    public int nextButtonPressSerial() {
        this.buttonPressSerial = this.display.nextSerial();
        return getButtonPressSerial();
    }

    public int nextButtonReleaseSerial() {
        this.buttonReleaseSerial = this.display.nextSerial();
        return getButtonReleaseSerial();
    }

    public int getButtonPressSerial() {
        return this.buttonPressSerial;
    }

    public int getButtonReleaseSerial() {
        return this.buttonReleaseSerial;
    }

    public boolean isButtonPressed(@Nonnegative final int button) {
        return this.pressedButtons.contains(button);
    }

    /**
     * Listen for motion as soon as given surface is grabbed.
     * <p>
     * If another surface already has the grab, the listener
     * is never registered.
     *
     * @param surfaceResource   Surface that is grabbed.
     * @param buttonPressSerial Serial that triggered the grab.
     * @param pointerGrabMotion Motion listener.
     *
     * @return true if the listener was registered, false if not.
     */
    public boolean grabMotion(@Nonnull final WlSurfaceResource surfaceResource,
                              final int buttonPressSerial,
                              @Nonnull final PointerGrabMotion pointerGrabMotion) {
        if (!getGrab().isPresent() ||
            !getGrab().get()
                      .equals(surfaceResource) ||
            getButtonPressSerial() != buttonPressSerial) {
            //preconditions not met
            return false;
        }

        final Listener motionListener = new Listener() {
            @Subscribe
            public void handle(final Motion motion) {
                if (getGrab().isPresent() &&
                    getGrab().get()
                             .equals(surfaceResource)) {
                    //there is pointer motion
                    pointerGrabMotion.motion(motion);
                }
                else {
                    //another surface has the grab, stop listening for pointer motion.
                    PointerDevice.this.unregister(this);
                    //stop listening for destroy event
                    remove();
                }
            }

            @Override
            public void handle() {
                //the surface was destroyed, stop listening for destroy event
                remove();
                //stop listening for pointer motion.
                PointerDevice.this.unregister(this);
            }
        };
        //listen for pointer motion
        register(motionListener);
        //listen for surface destruction
        surfaceResource.addDestroyListener(motionListener);

        //TODO should we listen for pointer resource destruction and unregister the listener?

        return true;
    }

    public void unregister(@Nonnull final Object listener) {
        this.inputBus.unregister(listener);
    }

    public void register(@Nonnull final Object listener) {
        this.inputBus.register(listener);
    }

    public void removeCursor(final WlPointerResource wlPointerResource,
                             final int serial) {
        if (serial == getEnterSerial()) {
            this.cursors.remove(wlPointerResource.getClient())
                        .hide();
        }
    }

    public void setCursor(final WlPointerResource wlPointerResource,
                          final int serial,
                          final WlSurfaceResource wlSurfaceResource,
                          final int hotspotX,
                          final int hotspotY) {

        if (serial != getEnterSerial()) {
            return;
        }

        Cursor clientCursor = this.cursors.get(wlPointerResource.getClient());
        final Point hotspot = Point.create(hotspotX,
                                           hotspotY);

        if (clientCursor == null) {
            clientCursor = this.cursorFactory.create(wlSurfaceResource,
                                                     hotspot);
            wlPointerResource.addDestroyListener(new Listener() {
                @Override
                public void handle() {
                    remove();
                    Optional.ofNullable(PointerDevice.this.cursors.remove(wlPointerResource.getClient()))
                            .ifPresent(Cursor::hide);
                }
            });
            this.cursors.put(wlPointerResource.getClient(),
                             clientCursor);
        }
        else {
            clientCursor.setWlSurfaceResource(wlSurfaceResource);
            clientCursor.setHotspot(hotspot);
        }

        clientCursor.show();
        updateActiveCursor();

        final WlSurface wlSurface = (WlSurface) wlSurfaceResource.getImplementation();
        final Surface   surface   = wlSurface.getSurface();
        surface.setState(updateCursorSurfaceState(wlSurfaceResource,
                                                  surface.getState()));
    }

    private SurfaceState updateCursorSurfaceState(final WlSurfaceResource wlSurfaceResource,
                                                  final SurfaceState surfaceState) {
        final SurfaceState.Builder surfaceStateBuilder = surfaceState.toBuilder();

        surfaceStateBuilder.inputRegion(Optional.of(this.nullRegion));
        surfaceStateBuilder.buffer(Optional.<WlBufferResource>empty());

        this.activeCursor.ifPresent(clientCursor -> {
            if (clientCursor.getWlSurfaceResource()
                            .equals(wlSurfaceResource) && !clientCursor.isHidden()) {
                //set back the buffer we cleared.
                surfaceStateBuilder.buffer(surfaceState.getBuffer());
                //move visible cursor to top of surface stack
                this.compositor.getSurfacesStack()
                               .remove(wlSurfaceResource);
                this.compositor.getSurfacesStack()
                               .addLast(wlSurfaceResource);
            }
        });

        return surfaceStateBuilder.build();
    }

    @Override
    public void beforeCommit(final WlSurfaceResource wlSurfaceResource) {
        final WlSurface wlSurface = (WlSurface) wlSurfaceResource.getImplementation();
        final Surface   surface   = wlSurface.getSurface();

        surface.setPendingState(updateCursorSurfaceState(wlSurfaceResource,
                                                         surface.getPendingState()));
    }

    @Override
    public void afterDestroy(final WlSurfaceResource wlSurfaceResource) {
        Optional.ofNullable(this.cursors.remove(wlSurfaceResource.getClient()))
                .ifPresent(Cursor::hide);
    }
}