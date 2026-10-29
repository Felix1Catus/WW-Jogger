/*
    Copyright 2024 Will Winder

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.willwinder.ugs.nbp.radialjog;

import com.willwinder.ugs.nbp.lib.Mode;
import com.willwinder.ugs.nbp.lib.services.LocalizingService;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.model.Axis;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.PartialPosition;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.UnitUtils;
import com.willwinder.universalgcodesender.model.events.ControllerStateEvent;
import com.willwinder.universalgcodesender.model.events.SettingChangedEvent;
import com.willwinder.universalgcodesender.services.JogService;
import com.willwinder.universalgcodesender.services.LookupService;
import net.miginfocom.swing.MigLayout;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;

/**
 * A FluidNC-style radial jog panel for the UGS platform. Reuses the standard
 * {@link JogService} backend; each ring on the pad jogs by an explicit distance
 * in millimetres.
 *
 * @author Claude
 */
@TopComponent.Description(
        preferredID = "RadialJogTopComponent"
)
@TopComponent.Registration(
        mode = Mode.LEFT_BOTTOM,
        openAtStartup = true,
        position = 5100)
@ActionID(
        category = RadialJogTopComponent.CATEGORY,
        id = RadialJogTopComponent.ACTION_ID)
@ActionReference(
        path = RadialJogTopComponent.WINDOW_PATH)
@TopComponent.OpenActionRegistration(
        displayName = "Radial Jog",
        preferredID = "RadialJogTopComponent"
)
public final class RadialJogTopComponent extends TopComponent implements UGSEventListener, RadialJogListener {

    public static final String WINDOW_PATH = LocalizingService.MENU_WINDOW_PLUGIN;
    public static final String CATEGORY = LocalizingService.CATEGORY_WINDOW;
    public static final String ACTION_ID = "com.willwinder.ugs.nbp.radialjog.RadialJogTopComponent";

    private final transient BackendAPI backend;
    private final transient JogService jogService;
    private final RadialJogControl control;
    private final JSpinner feedRateSpinner;
    private boolean updating = false;

    public RadialJogTopComponent() {
        backend = LookupService.lookup(BackendAPI.class);
        jogService = LookupService.lookup(JogService.class);

        control = new RadialJogControl(this);

        feedRateSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, 1_000_000, 100));
        feedRateSpinner.addChangeListener(e -> {
            if (!updating) {
                jogService.setFeedRate(((Number) feedRateSpinner.getValue()).doubleValue());
            }
        });

        JPanel south = new JPanel(new MigLayout("insets 4 8 6 8, fillx", "[right][grow][]", "[]"));
        south.add(new JLabel("Feed rate"));
        south.add(feedRateSpinner, "growx");
        south.add(new JLabel("mm/min"));

        setLayout(new BorderLayout());
        add(control, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        updateControls();
    }

    @Override
    protected void componentOpened() {
        super.componentOpened();
        setName("Radial Jog");
        setToolTipText("FluidNC-style radial jog pad");
        backend.addUGSEventListener(this);
        updateControls();
    }

    @Override
    protected void componentClosed() {
        super.componentClosed();
        backend.removeUGSEventListener(this);
    }

    @Override
    public void UGSEvent(UGSEvent event) {
        if (event instanceof ControllerStateEvent || event instanceof SettingChangedEvent) {
            updateControls();
        }
    }

    private void updateControls() {
        boolean canJog = jogService.canJog();
        control.setEnabled(canJog);
        feedRateSpinner.setEnabled(canJog);
        control.repaint();

        updating = true;
        try {
            feedRateSpinner.setValue(jogService.getFeedRate());
        } finally {
            updating = false;
        }
    }

    @Override
    public void onJog(int dx, int dy, int dz, double distanceMm) {
        if (!jogService.canJog()) {
            return;
        }
        Double x = dx == 0 ? null : dx * distanceMm;
        Double y = dy == 0 ? null : dy * distanceMm;
        Double z = dz == 0 ? null : dz * distanceMm;
        PartialPosition distance = new PartialPosition(x, y, z, UnitUtils.Units.MM);
        jogService.adjustManualLocation(distance, 1.0);
    }

    @Override
    public void onStop() {
        jogService.cancelJog();
    }

    @Override
    public void onHomeAll() {
        try {
            backend.performHomingCycle();
        } catch (Exception e) {
            // Homing may be unavailable; nothing more we can do here.
        }
    }

    @Override
    public void onReturnToZero(char axis) {
        Axis target = axis == 'X' ? Axis.X : axis == 'Y' ? Axis.Y : Axis.Z;
        backend.returnToZero(target);
    }
}
