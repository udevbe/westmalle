package org.westmalle.wayland.platform.newt.eglkms;


import com.jogamp.nativewindow.DefaultGraphicsScreen;
import com.jogamp.newt.MonitorDevice;
import com.jogamp.newt.MonitorMode;

import org.westmalle.wayland.platform.newt.eglkms.drm.DrmLibrary;
import org.westmalle.wayland.platform.newt.eglkms.drm.drmModeConnection;
import org.westmalle.wayland.platform.newt.eglkms.drm.drmModeConnector;
import org.westmalle.wayland.platform.newt.eglkms.drm.drmModeEncoder;
import org.westmalle.wayland.platform.newt.eglkms.drm.drmModeRes;

import jogamp.newt.MonitorModeProps;
import jogamp.newt.ScreenImpl;

public class ScreenDriver extends ScreenImpl{

    private final DrmLibrary drmLibrary;

    public ScreenDriver(final DrmLibrary drmLibrary) {
        this.drmLibrary = drmLibrary;
    }

    @Override
    protected void createNativeImpl() {
        init();
        aScreen = new DefaultGraphicsScreen(getDisplay().getGraphicsDevice(), screen_idx);



    }



    private void init() {

    DisplayDriver displayDriver = (DisplayDriver) getDisplay();

    /* Find the first available connector with modes */

        drmModeConnector connector = null;

        int i;

        drmModeRes resources = this.drmLibrary.drmModeGetResources(displayDriver.getFd());
        if (resources == null) {
            System.err.println("drmModeGetResources failed\n");
            System.exit(1);
        }

        for (i = 0; i < resources.count_connectors; i++) {
            connector = this.drmLibrary.drmModeGetConnector(displayDriver.getFd(), resources.connectors.getInt(i*4));
            if (connector == null)
                continue;

            if (connector.connection == drmModeConnection.DRM_MODE_CONNECTED &&
                connector.count_modes > 0)
                break;

            this.drmLibrary.drmModeFreeConnector(connector);
        }

        if (i == resources.count_connectors) {
            System.err.println("No currently active connector found.\n");
            System.exit(1);
        }

        for (i = 0; i < resources.count_encoders; i++) {
            drmModeEncoder
                    encoder = this.drmLibrary.drmModeGetEncoder(displayDriver.getFd(), resources.encoders.getInt(i*4));

            if (encoder == null)
                continue;

            if (encoder.encoder_id == connector.encoder_id)
                break;

            this.drmLibrary.drmModeFreeEncoder(encoder);
        }

    }

    @Override
    protected void closeNativeImpl() {

    }

    @Override
    protected int validateScreenIndex(final int idx) {
        return 0;
    }

    @Override
    protected void collectNativeMonitorModesAndDevicesImpl(final MonitorModeProps.Cache cache) {
        //TODO collect info from init method
    }

    @Override
    protected MonitorMode queryCurrentMonitorModeImpl(final MonitorDevice monitor) {
        //TODO collect info from init method

        return null;
    }

    @Override
    protected boolean setCurrentMonitorModeImpl(final MonitorDevice monitor, final MonitorMode mode) {
        //TODO collect info from init method

        return false;
    }
}