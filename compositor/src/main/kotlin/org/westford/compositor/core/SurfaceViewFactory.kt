package org.westford.compositor.core

import org.freedesktop.wayland.server.WlSurfaceResource
import org.westford.compositor.protocol.WlSurface
import javax.inject.Inject

class SurfaceViewFactory @Inject internal constructor(private val privateSurfaceViewFactory: PrivateSurfaceViewFactory) {

    internal fun create(wlSurfaceResource: WlSurfaceResource,
                        globalPosition: Point): SurfaceView {

        val wlSurface = wlSurfaceResource.implementation as WlSurface
        val surface = wlSurface.surface
        val surfaceTransform = surface.transform

        val positionTransform = Transforms.TRANSLATE(globalPosition.x,
                                                     globalPosition.y)

        val transform = positionTransform * surfaceTransform
        val inverseTransform = transform.invert()

        val surfaceView = this.privateSurfaceViewFactory.create(wlSurfaceResource,
                                                                positionTransform,
                                                                transform,
                                                                inverseTransform)
        surface.applySurfaceStateSignal.connect {
            surfaceView.onApply(it)
        }

        return surfaceView
    }
}
