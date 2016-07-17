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
package org.westmalle.wayland.dispmanx;


import com.google.auto.factory.AutoFactory;
import org.westmalle.wayland.core.Connector;
import org.westmalle.wayland.core.Renderer;
import org.westmalle.wayland.core.events.RenderBegin;
import org.westmalle.wayland.core.events.RenderEndAfterSwap;
import org.westmalle.wayland.core.events.RenderEndBeforeSwap;
import org.westmalle.wayland.core.events.Signal;
import org.westmalle.wayland.core.events.Slot;
import org.westmalle.wayland.protocol.WlOutput;

import javax.annotation.Nonnull;

@AutoFactory(allowSubclasses = true,
             className = "DispmanxConnectorFactory")
public class DispmanxConnector implements Connector {

    private final Signal<RenderBegin, Slot<RenderBegin>>                 renderBeginSignal         = new Signal<>();
    private final Signal<RenderEndBeforeSwap, Slot<RenderEndBeforeSwap>> renderEndBeforeSwapSignal = new Signal<>();
    private final Signal<RenderEndAfterSwap, Slot<RenderEndAfterSwap>>   renderEndAfterSwapSignal  = new Signal<>();

    private final WlOutput wlOutput;
    private final int      dispmanxElement;

    DispmanxConnector(final WlOutput wlOutput,
                      final int dispmanxElement) {
        this.wlOutput = wlOutput;
        this.dispmanxElement = dispmanxElement;
    }

    @Nonnull
    @Override
    public WlOutput getWlOutput() {
        return this.wlOutput;
    }

    @Override
    public Signal<RenderBegin, Slot<RenderBegin>> getRenderBeginSignal() {
        return this.renderBeginSignal;
    }

    @Override
    public Signal<RenderEndBeforeSwap, Slot<RenderEndBeforeSwap>> getRenderEndBeforeSwapSignal() {
        return this.renderEndBeforeSwapSignal;
    }

    @Override
    public Signal<RenderEndAfterSwap, Slot<RenderEndAfterSwap>> getRenderEndAfterSwapSignal() {
        return this.renderEndAfterSwapSignal;
    }


    public int getDispmanxElement() {
        return this.dispmanxElement;
    }

    @Override
    public void accept(@Nonnull final Renderer renderer) {
        renderer.visit(this);
    }
}
