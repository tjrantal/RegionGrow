/*
 Image/J Plugins
 Copyright (C) 2012 Timo Rantalainen
 Author's email: tjrantal at gmail dot com
 The code is licenced under GPL 2.0 or newer
 */
package	ijGrower;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;


/*
 Performs connected region growing. User is asked to provide the seed area points.
 Result is displayed as a binary image. Works with 3D images stack.
 */

public class IJGrower implements PlugIn {
	private int[] seedPoints;
	private double diffLimit;
    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        /*Check that an image was open*/
		if (imp == null) {
            IJ.noImage();
            return;
        }
		/*Get image size and stack depth*/
		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getStackSize();
		if (depth < 2){
			IJ.error("IJGrower works on image stacks");
		}
		/*Check that the image is 16 bit gray image*/
		if (imp.getType() != ImagePlus.GRAY16){
			IJ.error("IJGrower expects 16-bit greyscale data, e.g. DICOM-image");
			return;
		}
		
		/*Get seed volume and max diff*/
		if (!getParameters()){
			IJ.error("IJGrower needs seed volume and maximum difference");
		}
		
		ImageStack stack = imp.getStack();
		final Object[] imageArrayPointers = stack.getImageArray();
		
		/*Construct a 3D memory stack*/
        double [][][] image3D = new double[width][height][depth];
		short[] temp;
        for (int d = 0; d < depth; ++d) {
            temp = (short[]) imageArrayPointers[d];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					image3D[r][c][d] = (double) temp[c+r*width];
				}
			}
        }
		
		/*Construct the segmented mask*/
		double [][][] mask3D = new double[width][height][depth];	/*Initialized to zero by Java as default*/
		/*Create Seed volume, experimentally chosen....*/
		for (int d = seedPoints[4]; d < seedPoints[5]; ++d) {
			for (int r = seedPoints[2];r<seedPoints[3];++r){
				for (int c = seedPoints[0];c<seedPoints[1];++c){
					mask3D[r][c][d] = (double) 1;
				}
			}
        }
		
		RegionGrow3D r3d = new RegionGrow3D(image3D, mask3D, diffLimit);
		
        ImageStack resultStack = createOutputStack(r3d.segmentationMask);

        new ImagePlus("Region", resultStack).show();
    }
	
	/*Visual mask result*/
	private ImageStack createOutputStack(double[][][] mask3d) {
		int width	=mask3d[0].length;
		int height	=mask3d.length;
		int depth	=mask3d[0][0].length;
        ImageStack resultStack = new ImageStack(width, height);
        int pixels = width*height;
        for (int d = 0; d < depth; ++d) {
            byte[] slicePixels = new byte[pixels];
			
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					slicePixels[c+r*width] = (byte) (mask3d[r][c][d]*127.0);
				}
			}
            resultStack.addSlice(null, slicePixels);
        }
        return resultStack;
    }

    /**
     * Get seed volume and maximum difference from user.
     *
     * @return <code>true</code> when user clicked OK (confirmed changes, <code>false</code>
     *         otherwise.
     */
	 
    private boolean getParameters() {	
		/*Create dialog*/
        final GenericDialog gd = new GenericDialog("Grow options");
        gd.addMessage("Seed volume coordinates");
        gd.addNumericField("xLow", 169, 0);
		gd.addNumericField("xHigh", 250, 0);
        gd.addNumericField("yLow", 370, 0);
		gd.addNumericField("yHigh", 390, 0);
        gd.addNumericField("zLow", 8, 0);
		gd.addNumericField("zHigh", 12, 0);
        gd.addMessage("Maximum difference");
        gd.addNumericField("maxDiff", 100, 0);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return false;
        }
		seedPoints = new int[6];
		/*Get the values*/
		for (int i = 0; i<seedPoints.length;++i){
			seedPoints[i] = (int) gd.getNextNumber();
		}
        diffLimit = gd.getNextNumber();

        return true;
    }
	
}
