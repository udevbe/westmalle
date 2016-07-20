//Copyright 2016 Erik De Rijcke
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
package org.westmalle.wayland.x11.egl;


import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.EventLoop;
import org.freedesktop.wayland.server.EventSource;
import org.westmalle.wayland.core.EglConnector;
import org.westmalle.wayland.core.Renderer;
import org.westmalle.wayland.protocol.WlOutput;
import org.westmalle.wayland.x11.X11Connector;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@AutoFactory(allowSubclasses = true,
             className = "X11EglConnectorFactory")
public class X11EglConnector implements EglConnector {

    @Nonnull
    private final X11Connector x11Connector;
    @Nonnull
    private final Display      display;
    private final long         eglSurface;
    private final long         eglContext;
    private final long         eglDisplay;

    private final AtomicBoolean rendererAvailable = new AtomicBoolean(true);

    X11EglConnector(@Nonnull @Provided final Display display,
                    @Nonnull final X11Connector x11Connector,
                    final long eglSurface,
                    final long eglContext,
                    final long eglDisplay) {
        this.display = display;
        this.x11Connector = x11Connector;
        this.eglSurface = eglSurface;
        this.eglContext = eglContext;
        this.eglDisplay = eglDisplay;
    }

    @Override
    public long getEglSurface() {
        return this.eglSurface;
    }

    @Override
    public long getEglContext() {
        return this.eglContext;
    }

    @Override
    public long getEglDisplay() {
        return this.eglDisplay;
    }

    @Nonnull
    @Override
    public WlOutput getWlOutput() {
        return this.x11Connector.getWlOutput();
    }

    @Nonnull
    public X11Connector getX11Connector() {
        return this.x11Connector;
    }

    @Override
    public void accept(@Nonnull final Renderer renderer) {
        //TODO unit test 2 cases here: schedule idle, no-op when already scheduled
        whenIdle(() -> renderOn(renderer));
    }

    private void whenIdle(final EventLoop.IdleHandler idleHandler) {
        if (this.rendererAvailable.compareAndSet(true,
                                                 false)) {
            this.display.getEventLoop()
                        .addIdle(idleHandler);
        }
    }

    private void renderOn(@Nonnull final Renderer renderer) {
        renderer.visit(this);
        this.display.flushClients();
        this.rendererAvailable.set(true);
    }
}
