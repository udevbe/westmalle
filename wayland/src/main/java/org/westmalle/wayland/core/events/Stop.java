package org.westmalle.wayland.core.events;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Stop {
    public static Stop create() {
        return new AutoValue_Stop();
    }
}
