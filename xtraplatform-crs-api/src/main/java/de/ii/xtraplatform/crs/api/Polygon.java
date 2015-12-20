package de.ii.xtraplatform.crs.api;

import java.util.List;

/**
 *
 * @author fischer
 */
public class Polygon extends Geometry {

    private List<List<List<Double>>> rings;
    
    public Polygon() {
    }

    public Polygon(double[] coordinates, EpsgCrs sr) {
        this();
        this.coordinates = coordinates;
        this.spatialReference = sr;
    }


    public Polygon(String[] coordinates, EpsgCrs sr) {
        this(parseCoordinates(coordinates), sr);
    }

    public Polygon(String[] coordinates, String srs) {
        this(coordinates, new EpsgCrs(Integer.parseInt(srs)));
    }

    public void setRings(List<List<List<Double>>> rings) {
        this.rings = rings;
    }
    
    public List<List<List<Double>>> getRings() {
        return rings;
    }

    private static double[] parseCoordinates(String[] coordinates) {
        double[] doubleCoordinates = new double[coordinates.length];

        for(int i = 0; i < coordinates.length; i++) {
            doubleCoordinates[i] = Double.parseDouble(coordinates[i]);
        }

        return doubleCoordinates;
    }
}
