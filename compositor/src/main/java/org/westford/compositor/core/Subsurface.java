/*
 * Westford Wayland Compositor.
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
package org.westford.compositor.core;

import com.google.auto.factory.AutoFactory;
import org.freedesktop.wayland.server.WlSurfaceResource;
import org.westford.Signal;
import org.westford.Slot;
import org.westford.compositor.protocol.WlSurface;

import javax.annotation.Nonnull;
import java.util.LinkedList;

@AutoFactory(allowSubclasses = true,
             className = "PrivateSubsurfaceFactory")
public class Subsurface implements Role {

    @Nonnull
    private final WlSurfaceResource parentWlSurfaceResource;

    private final Signal<Boolean, Slot<Boolean>> effectiveSyncSignal = new Signal<>();
    private       boolean                        effectiveSync       = true;

    private boolean inert = false;
    private boolean sync  = true;

    @Nonnull
    private Sibling sibling;

    @Nonnull
    private SurfaceState surfaceState;
    @Nonnull
    private SurfaceState cachedSurfaceState;

    @Nonnull
    private Point position = Point.ZERO;

    Subsurface(@Nonnull final WlSurfaceResource parentWlSurfaceResource,
               @Nonnull final Sibling sibling,
               @Nonnull final SurfaceState surfaceState) {
        this.parentWlSurfaceResource = parentWlSurfaceResource;
        this.sibling = sibling;
        this.surfaceState = surfaceState;
        this.cachedSurfaceState = surfaceState;
    }

    @Override
    public void beforeCommit(@Nonnull final WlSurfaceResource wlSurfaceResource) {
        if (isInert()) {
            return;
        }

        if (isEffectiveSync()) {
            final WlSurface wlSurface = (WlSurface) wlSurfaceResource.getImplementation();
            final Surface   surface   = wlSurface.getSurface();

            //set back cached state so surface can do eg. buffer release
            surface.setState(getCachedSurfaceState());
        }
    }

    @Override
    public void accept(@Nonnull final RoleVisitor roleVisitor) {
        roleVisitor.visit(this);
    }

    public boolean isInert() {
        return this.inert;
    }

    public boolean isEffectiveSync() {
        return this.effectiveSync;
    }

    @Nonnull
    public SurfaceState getCachedSurfaceState() {
        return this.cachedSurfaceState;
    }

    public void setInert() {
        this.inert = true;
    }

    public void onParentApply() {
        if (isInert()) {
            return;
        }

        final SurfaceState cachedSurfaceState = getCachedSurfaceState();
        if (isEffectiveSync() &&
            !getSurfaceState().equals(cachedSurfaceState)) {
            //sync mode. update old state with cached state
            this.surfaceState = cachedSurfaceState;
            apply(cachedSurfaceState);
        }
    }

    @Nonnull
    public SurfaceState getSurfaceState() {
        return this.surfaceState;
    }

    public void apply(final SurfaceState surfaceState) {
        if (isInert()) {
            return;
        }

        if (isEffectiveSync()) {
            final WlSurface wlSurface = (WlSurface) getSibling().getWlSurfaceResource()
                                                                .getImplementation();
            final Surface      surface         = wlSurface.getSurface();
            final SurfaceState oldSurfaceState = getSurfaceState();
            if (!surface.getState()
                        .equals(oldSurfaceState)) {
                //replace new state with old state
                getSibling().setPosition(this.position);
                surface.apply(oldSurfaceState);
                this.cachedSurfaceState = surfaceState;
            }
        }
        else {
            //desync mode, our 'old' state is always the newest state.
            this.cachedSurfaceState = surfaceState;
            this.surfaceState = surfaceState;
            getSibling().setPosition(this.position);
        }
    }

    @Nonnull
    public Sibling getSibling() {
        return this.sibling;
    }

    @Nonnull
    public WlSurfaceResource getParentWlSurfaceResource() {
        return this.parentWlSurfaceResource;
    }

    public void setSync(final boolean sync) {
        if (isInert()) {
            return;
        }

        this.sync = sync;

        final WlSurface parentWlSurface = (WlSurface) getParentWlSurfaceResource().getImplementation();
        parentWlSurface.getSurface()
                       .getRole()
                       .ifPresent(role -> role.accept(new RoleVisitor() {
                           @Override
                           public void visit(final Subsurface parentSubsurface) {
                               //TODO unit test this
                               updateEffectiveSync(parentSubsurface.isEffectiveSync());
                           }

                           @Override
                           public void defaultAction(final Role role) {
                               updateEffectiveSync(false);
                           }
                       }));
    }

    public void updateEffectiveSync(final boolean parentEffectiveSync) {
        final boolean oldEffectiveSync = this.effectiveSync;
        this.effectiveSync = this.sync || parentEffectiveSync;

        if (oldEffectiveSync != isEffectiveSync()) {
            /*
             * If we were in sync mode and now our effective mode is desync, we have to apply our cached state
             * immediately
             */
            //TODO unit test this
            if (!isEffectiveSync()) {
                final WlSurface wlSurface = (WlSurface) getSibling().getWlSurfaceResource()
                                                                    .getImplementation();
                wlSurface.getSurface()
                         .apply(getCachedSurfaceState());
            }

            getEffectiveSyncSignal().emit(isEffectiveSync());
        }
    }

    public Signal<Boolean, Slot<Boolean>> getEffectiveSyncSignal() {
        return this.effectiveSyncSignal;
    }

    public void above(@Nonnull final WlSurfaceResource sibling) {
        if (isInert()) {
            return;
        }

        placement(false,
                  sibling);
    }

    private void placement(final boolean below,
                           final WlSurfaceResource siblingWlSurfaceResource) {

        final WlSurfaceResource   parentWlSurfaceResource = getParentWlSurfaceResource();
        final WlSurface           parentWlSurface         = (WlSurface) parentWlSurfaceResource.getImplementation();
        final Surface             parentSurface           = parentWlSurface.getSurface();
        final LinkedList<Sibling> siblings                = parentSurface.getSiblings();

        int siblingIndex = -1;
        int thisIndex    = -1;

        for (int i = 0; i < siblings.size(); i++) {
            final Sibling sibling = siblings.get(i);

            if (sibling.equals(Sibling.create(siblingWlSurfaceResource))) {
                siblingIndex = i;
            }
            else if (sibling.equals(getSibling())) {
                thisIndex = i;
            }

            if (siblingIndex != -1 && thisIndex != -1) {
                break;
            }
        }

        //FIXME if siblingIndex == -1 then we have a (client) protocol error, else we have a bug.

        siblings.add(below ? siblingIndex : siblingIndex + 1,
                     siblings.remove(thisIndex));

        //Note: committing the subsurface stack happens in the parent surface.
    }

    public void below(@Nonnull final WlSurfaceResource sibling) {
        if (isInert()) {
            return;
        }

        placement(true,
                  sibling);
    }

    public void setPosition(@Nonnull final Point position) {
        if (isInert()) {
            return;
        }

        this.position = position;
    }
}
