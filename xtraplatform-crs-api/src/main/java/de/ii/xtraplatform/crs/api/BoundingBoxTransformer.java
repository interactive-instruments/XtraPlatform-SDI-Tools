package de.ii.xtraplatform.crs.api;

/**
 *
 * @author fischer
 */
public abstract class BoundingBoxTransformer implements CrsTransformer{
        
    @Override
    public BoundingBox transformBoundingBox(BoundingBox bbox, EpsgCrs targetCrs) throws CrsTransformationException {
                
        // DEBUG
        //System.out.println( bbox.getXmin()+" " +bbox.getYmin()+" "+bbox.getXmax()+" "+bbox.getYmax()+" 0;");
        CoordinateTuple ll = this.transform(bbox.getXmin(), bbox.getYmin());
        CoordinateTuple lr = this.transform(bbox.getXmax(), bbox.getYmin());
        CoordinateTuple ur = this.transform(bbox.getXmax(), bbox.getYmax());
        CoordinateTuple ul = this.transform(bbox.getXmin(), bbox.getYmax());

        if (ll.isNull() || ul.isNull() || lr.isNull() || ur.isNull()) {
            throw new CrsTransformationException();
        }

        BoundingBox envOut = new BoundingBox();
        envOut.setEpsgCrs(targetCrs);
              
        // Die BoundingBox ins boxIn (lx,ly,hx,hy) im System crsIn wird in das System 
        // crsOut transformiert. Dabei wird sichergestellt, dass es sich wieder um eine
        // BoundingBox handelt. D.h. in Wirklichkeit werden alle vier Eckpunkte der
        // Input-BoundingBox transformiert und davon wird die Output-BoundingBox durch
        // Minmaxing bestimmt.
        
        if (ul.getX() < ll.getX()) {
            envOut.setXmin(ul.getX());
        } else {
            envOut.setXmin(ll.getX());
        }
        if (lr.getY() < ll.getY()) {
            envOut.setYmin(lr.getY());
        } else {
            envOut.setYmin(ll.getY());
        }
        if (ur.getX() > lr.getX()) {
            envOut.setXmax(ur.getX());
        } else {
            envOut.setXmax(lr.getX());
        }
        if (ul.getY() > ur.getY()) {
            envOut.setYmax(ul.getY());
        } else {
            envOut.setYmax(ur.getY());
        }

        // DEBUG
        //System.out.println( envOut.getXmin()+" " +envOut.getYmin()+" "+envOut.getXmax()+" "+envOut.getYmax()+" 1;");
        return envOut;        
    }
}
