package org.westmalle.wayland.core.events;

import com.google.auto.value.AutoValue;
import org.freedesktop.wayland.server.WlKeyboardResource;

import javax.annotation.Nonnull;
import java.util.Set;

@AutoValue
public abstract class KeyboardFocusLost {
    public static KeyboardFocusLost create(@Nonnull final Set<WlKeyboardResource> wlKeyboardResources) {
        return new AutoValue_KeyboardFocusLost(wlKeyboardResources);
    }

    public abstract Set<WlKeyboardResource> getWlKeyboardResources();
}
