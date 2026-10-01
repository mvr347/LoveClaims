package me.lovelace.loveclaims.task;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParticleBorderTest {

    private static boolean onEdge(double[] p, BoundingBox b) {
        int extremes = 0;
        if (p[0] == b.getMinX() || p[0] == b.getMaxX()) extremes++;
        if (p[1] == b.getMinY() || p[1] == b.getMaxY()) extremes++;
        if (p[2] == b.getMinZ() || p[2] == b.getMaxZ()) extremes++;
        return extremes >= 2;
    }

    @Test
    void smallBoxGetsAboutOnePointPerBlockOnEdgesOnly() {
        BoundingBox box = new BoundingBox(0, 0, 0, 4, 4, 4);
        List<double[]> pts = ParticleBorder.outlinePoints(box, 600);
        assertFalse(pts.isEmpty());
        for (double[] p : pts) assertTrue(onEdge(p, box), "point off the edges");
    }

    @Test
    void bigBoxIsThinnedToTheLimit() {
        BoundingBox box = new BoundingBox(0, 0, 0, 500, 200, 500);
        assertTrue(ParticleBorder.outlinePoints(box, 600).size() <= 600);
    }

    @Test
    void cornersAreAlwaysPresent() {
        BoundingBox box = new BoundingBox(0, 0, 0, 10, 10, 10);
        List<double[]> pts = ParticleBorder.outlinePoints(box, 600);
        assertTrue(pts.stream().anyMatch(p -> p[0] == 0 && p[1] == 0 && p[2] == 0));
        assertTrue(pts.stream().anyMatch(p -> p[0] == 10 && p[1] == 10 && p[2] == 10));
    }
}
