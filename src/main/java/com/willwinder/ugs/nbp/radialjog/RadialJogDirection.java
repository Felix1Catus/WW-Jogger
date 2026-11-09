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

/**
 * The four cardinal jog directions on the radial pad. The angle from the centre
 * of the pad selects one of these; {@code dx}/{@code dy} are the signs applied
 * to the selected ring distance. Screen "up" is Y+. Diagonal moves are not
 * supported: any drag resolves to the nearest cardinal axis.
 *
 * @author Claude
 */
public enum RadialJogDirection {
    E(1, 0, 0),
    N(0, 1, 90),
    W(-1, 0, 180),
    S(0, -1, 270);

    private final int dx;
    private final int dy;
    private final int centerDegrees;

    RadialJogDirection(int dx, int dy, int centerDegrees) {
        this.dx = dx;
        this.dy = dy;
        this.centerDegrees = centerDegrees;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getCenterDegrees() {
        return centerDegrees;
    }

    /**
     * Returns the direction whose sector contains the given math-convention
     * angle (degrees, 0 = +X / east, counter-clockwise positive).
     */
    public static RadialJogDirection fromAngle(double degrees) {
        double normalized = ((degrees % 360) + 360) % 360;
        switch ((int) Math.round(normalized / 90.0) % 4) {
            case 1:
                return N;
            case 2:
                return W;
            case 3:
                return S;
            default:
                return E;
        }
    }
}
