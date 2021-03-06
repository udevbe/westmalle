package org.westford.compositor.core;

import org.freedesktop.wayland.server.WlBufferResource;
import org.freedesktop.wayland.server.WlRegionResource;
import org.freedesktop.wayland.server.WlSurfaceResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.westford.compositor.protocol.WlRegion;
import org.westford.compositor.protocol.WlSurface;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SceneTest {

    @Mock
    private SceneLayer     backgroundLayer;
    @Mock
    private SceneLayer     underLayer;
    @Mock
    private SceneLayer     applicationLayer;
    @Mock
    private SceneLayer     overLayer;
    @Mock
    private SceneLayer     fullscreenLayer;
    @Mock
    private SceneLayer     lockLayer;
    @Mock
    private SceneLayer     cursorLayer;
    @Mock
    private InfiniteRegion infiniteRegion;

    private Scene scene;

    @Before
    public void setUp() {
        this.scene = new Scene(this.backgroundLayer,
                               this.underLayer,
                               this.applicationLayer,
                               this.overLayer,
                               this.fullscreenLayer,
                               this.lockLayer,
                               this.cursorLayer,
                               this.infiniteRegion);
    }

    @Test
    public void pickSurface() throws Exception {
        //given
        final Point global = mock(Point.class);

        final WlSurfaceResource wlSurfaceResource0 = mock(WlSurfaceResource.class);
        final WlSurface         wlSurface0         = mock(WlSurface.class);
        when(wlSurfaceResource0.getImplementation()).thenReturn(wlSurface0);
        final Surface surface0 = mock(Surface.class);
        when(wlSurface0.getSurface()).thenReturn(surface0);
        final SurfaceView surfaceView0 = mock(SurfaceView.class);
        when(surface0.getViews()).thenReturn(Collections.singleton(surfaceView0));
        when(surfaceView0.getWlSurfaceResource()).thenReturn(wlSurfaceResource0);
        when(surfaceView0.getParent()).thenReturn(Optional.empty());
        when(surfaceView0.isEnabled()).thenReturn(true);
        when(surfaceView0.isDrawable()).thenReturn(true);
        final Rectangle size0 = mock(Rectangle.class);
        when(surface0.getSize()).thenReturn(size0);
        final SurfaceState surfaceState0 = mock(SurfaceState.class);
        when(surface0.getState()).thenReturn(surfaceState0);
        final WlBufferResource wlBufferResource0 = mock(WlBufferResource.class);
        when(surfaceState0.getBuffer()).thenReturn(Optional.of(wlBufferResource0));
        final WlRegionResource wlRegionResource0 = mock(WlRegionResource.class);
        final WlRegion         wlRegion0         = mock(WlRegion.class);
        when(wlRegionResource0.getImplementation()).thenReturn(wlRegion0);
        final Region region0 = mock(Region.class);
        when(wlRegion0.getRegion()).thenReturn(region0);
        when(surfaceState0.getInputRegion()).thenReturn(Optional.of(region0));
        final Point position0 = mock(Point.class);
        when(surfaceView0.local(global)).thenReturn(position0);
        when(region0.contains(size0,
                              position0)).thenReturn(true);
        final LinkedList<Sibling> siblings0 = new LinkedList<>();
        siblings0.add(Sibling.Companion.create(wlSurfaceResource0));
        when(surface0.getSiblings()).thenReturn(siblings0);
        final Role role0 = mock(Role.class);
        when(surface0.getRole()).thenReturn(Optional.of(role0));

        final WlSurfaceResource wlSurfaceResource1 = mock(WlSurfaceResource.class);
        final WlSurface         wlSurface1         = mock(WlSurface.class);
        when(wlSurfaceResource1.getImplementation()).thenReturn(wlSurface1);
        final Surface surface1 = mock(Surface.class);
        when(wlSurface1.getSurface()).thenReturn(surface1);
        final SurfaceView surfaceView1 = mock(SurfaceView.class);
        when(surface1.getViews()).thenReturn(Collections.singleton(surfaceView1));
        when(surfaceView1.getWlSurfaceResource()).thenReturn(wlSurfaceResource1);
        when(surfaceView1.getParent()).thenReturn(Optional.empty());
        when(surfaceView1.isEnabled()).thenReturn(true);
        when(surfaceView1.isDrawable()).thenReturn(true);
        final Rectangle size1 = mock(Rectangle.class);
        when(surface1.getSize()).thenReturn(size1);
        final SurfaceState surfaceState1 = mock(SurfaceState.class);
        when(surface1.getState()).thenReturn(surfaceState1);
        final WlBufferResource wlBufferResource1 = mock(WlBufferResource.class);
        when(surfaceState1.getBuffer()).thenReturn(Optional.of(wlBufferResource1));
        final WlRegionResource wlRegionResource1 = mock(WlRegionResource.class);
        final WlRegion         wlRegion1         = mock(WlRegion.class);
        when(wlRegionResource1.getImplementation()).thenReturn(wlRegion1);
        final Region region1 = mock(Region.class);
        when(surfaceState1.getInputRegion()).thenReturn(Optional.of(region1));
        when(wlRegion1.getRegion()).thenReturn(region1);
        final Point position1 = mock(Point.class);
        when(surfaceView1.local(global)).thenReturn(position1);
        when(region1.contains(size1,
                              position1)).thenReturn(false);
        final LinkedList<Sibling> siblings1 = new LinkedList<>();
        siblings1.add(Sibling.Companion.create(wlSurfaceResource1));
        when(surface1.getSiblings()).thenReturn(siblings1);
        final Role role1 = mock(Role.class);
        when(surface1.getRole()).thenReturn(Optional.of(role1));

        final LinkedList<SurfaceView> views = new LinkedList<>();
        views.add(surfaceView0);
        views.add(surfaceView1);
        when(applicationLayer.getSurfaceViews()).thenReturn(views);

        //when
        final Optional<SurfaceView> pickSurface = this.scene.pickSurfaceView(global);

        //then
        assertThat(pickSurface.get()).isEqualTo(surfaceView0);
    }

    //TODO test if cursor views are selectable
    //TODO test if layers are properly respected
    //TODO test if outputscene is properly constructed
    //TODO test if sibling views are properly included
}