/*
 * Westmalle Wayland Compositor.
 * Copyright (C) 2016  Erik De Rijcke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.westmalle.wayland.bootstrap.drm.direct;

import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.WlKeyboardResource;
import org.westmalle.nativ.linux.InputEventCodes;
import org.westmalle.tty.Tty;
import org.westmalle.wayland.core.KeyBindingFactory;
import org.westmalle.wayland.core.KeyboardDevice;
import org.westmalle.wayland.core.LifeCycle;
import org.westmalle.wayland.core.PointerDevice;
import org.westmalle.wayland.core.TouchDevice;
import org.westmalle.wayland.protocol.WlKeyboard;
import org.westmalle.wayland.protocol.WlSeat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Boot {

    private static final Logger LOGGER   = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String BACK_END = "backEnd";

    public static void main_from_native() {
        try {
            //this main is not launched directly, but instead from native code that invokes the jvm after
            //it has properly configured OS signals, used for tty switching.

            configureLogger();
            LOGGER.info("Starting Westmalle");

            new Boot().strap(DaggerDirectDrmEglCompositor.create());
        }
        catch (final Throwable t) {
            LOGGER.throwing(Boot.class.getName(),
                            "main_from_native",
                            t);
            t.printStackTrace();
        }
    }

    private static void configureLogger() throws IOException {
        final FileHandler fileHandler = new FileHandler("westmalle.log");
        fileHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(fileHandler);

        Thread.setDefaultUncaughtExceptionHandler((thread,
                                                   throwable) -> {
            LOGGER.severe("Got uncaught exception " + throwable.getMessage());
            throwable.printStackTrace();
        });
    }

    public void strap(final DirectDrmEglCompositor drmEglCompositor) {

        final LifeCycle lifeCycle = drmEglCompositor.lifeCycle();

        /*
         * Create a libinput seat that will listen for native input events on seat0.
         */
        final WlSeat wlSeat = drmEglCompositor.seatFactory()
                                              .create("seat0",
                                                      "",
                                                      "",
                                                      "",
                                                      "",
                                                      "");

        /*
         * Setup keyboard focus tracking to follow mouse pointer & touch
         */
        final PointerDevice pointerDevice = wlSeat.getWlPointer()
                                                  .getPointerDevice();
        final TouchDevice touchDevice = wlSeat.getWlTouch()
                                              .getTouchDevice();

        final WlKeyboard              wlKeyboard          = wlSeat.getWlKeyboard();
        final KeyboardDevice          keyboardDevice      = wlKeyboard.getKeyboardDevice();
        final Set<WlKeyboardResource> wlKeyboardResources = wlKeyboard.getResources();

        pointerDevice.getPointerFocusSignal()
                     .connect(event -> keyboardDevice.setFocus(wlKeyboardResources,
                                                               pointerDevice.getFocus()));
        touchDevice.getTouchDownSignal()
                   .connect(event -> keyboardDevice.setFocus(wlKeyboardResources,
                                                             touchDevice.getGrab()));

        /*
         * setup tty switching key bindings
         */
        final Tty tty = drmEglCompositor.tty();

        //listen for tty switching signals
        final Display display = drmEglCompositor.display();
        display.getEventLoop()
               .addSignal(tty.getRelSig(),
                          signalNumber -> {
                              tty.handleVtLeave();
                              return 0;
                          });
        display.getEventLoop()
               .addSignal(tty.getAcqSig(),
                          signalNumber -> {
                              tty.handleVtEnter();
                              return 0;
                          });

        addTtyKeyBindings(drmEglCompositor,
                          keyboardDevice,
                          tty);

        /*
         * and finally, start the compositor
         */
        lifeCycle.start();
    }

    private void addTtyKeyBindings(final DirectDrmEglCompositor drmEglCompositor,
                                   final KeyboardDevice keyboardDevice,
                                   final Tty tty) {
        //TODO we don't want to switch the tty ourselves directly but instead signal our parent launch to do the switch.

        final KeyBindingFactory keyBindingFactory = drmEglCompositor.keyBindingFactory();

        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F1)),
                                 () -> tty.activate(1))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F2)),
                                 () -> tty.activate(2))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F3)),
                                 () -> tty.activate(3))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F4)),
                                 () -> tty.activate(4))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F5)),
                                 () -> tty.activate(5))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F6)),
                                 () -> tty.activate(6))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F7)),
                                 () -> tty.activate(7))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F8)),
                                 () -> tty.activate(8))
                         .enable();

        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F9)),
                                 () -> tty.activate(9))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F10)),
                                 () -> tty.activate(10))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F11)),
                                 () -> tty.activate(11))
                         .enable();
        keyBindingFactory.create(keyboardDevice,
                                 new HashSet<>(Arrays.asList(InputEventCodes.KEY_LEFTCTRL,
                                                             InputEventCodes.KEY_LEFTALT,
                                                             InputEventCodes.KEY_F12)),
                                 () -> tty.activate(12))
                         .enable();
    }
}