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
import ij.measure.Calibration;	/*For obtaining pixel dimensions from original stack...*/
import ij.gui.NewImage;			/*For creating the output stack images*/
import ij.process.ImageProcessor;		/*For setting output stack image properties*/
import ij.io.FileInfo;			/*For setting image voxel dimensions...*/
import java.util.Properties;	/*For getting image properties*/
import java.util.*;				/*For enumeration*/
/*
 Performs connected region growing. User is asked to provide the seed area points.
 Result is displayed as a binary image. Works with 3D images stack.
 */

public class IJGrower implements PlugIn {
	private int[] seedPoints;
	private double diffLimit;
	private boolean threeD;
	private boolean growUpDown;
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
		double [][][] segmentationMask;
		/*Create Seed volume, experimentally chosen....*/
		for (int d = seedPoints[4]; d < seedPoints[5]; ++d) {
			for (int r = seedPoints[2];r<seedPoints[3];++r){
				for (int c = seedPoints[0];c<seedPoints[1];++c){
					mask3D[r][c][d] = (double) 1;
				}
			}
        }
		/*Grow stack*/
		if (threeD){	/*3D region grow*/
			RegionGrow3D r3d = new RegionGrow3D(image3D, mask3D, diffLimit);
			segmentationMask = r3d.segmentationMask;
		}else{			/*2D region grow*/
			segmentationMask = new double[width][height][depth];	/*Create the segmentation mask*/
			/*Go through all of the slices*/
			double[][] sliceData = new double[width][height];
			double[][] sliceMask = new double[width][height];
			RegionGrow r2d;
			for (int d = 0; d < depth; ++d) {
				/*Get the slice*/
				for (int r = 0;r<height;++r){
					for (int c = 0;c<width;++c){
						sliceData[r][c] = image3D[r][c][d];
						sliceMask[r][c] = mask3D[r][c][d];
					}
				}
				/*Run the region growing*/
				r2d = new RegionGrow(sliceData,sliceMask,diffLimit);
				/*Copy the mask result to mask3D*/
				for (int r = 0;r<height;++r){
					for (int c = 0;c<width;++c){
						segmentationMask[r][c][d]=r2d.segmentationMask[r][c];
					}
				}
			}
			
			/*Grow up down too*/
			if(growUpDown){
				IJ.log("Into UD");
				/*Go through all of the slices*/
				sliceData = new double[width][depth];
				sliceMask = new double[width][depth];
				for (int r = 0;r<height;++r){
				
					/*Get the slice*/
					for (int d = 0; d < depth; ++d) {
						for (int c = 0;c<width;++c){
							sliceData[c][d] = image3D[r][c][d];
							sliceMask[c][d] = segmentationMask[r][c][d];
						}
					}
					/*Run the region growing*/
					r2d = new RegionGrow(sliceData,sliceMask,diffLimit);
					/*Copy the mask result to mask3D*/
					for (int d = 0; d < depth; ++d) {
						for (int c = 0;c<width;++c){
							segmentationMask[r][c][d]=r2d.segmentationMask[c][d];
						}
					}
				}
				
			}
			
		}
		
		
		/*Visualize result*/
		Calibration calibration = imp.getCalibration();
        ImagePlus resultStack = createOutputStack(segmentationMask, calibration);
		resultStack.show();
    }
	
	/*Visual mask result*/
	private ImagePlus createOutputStack(double[][][] mask3d, Calibration calibration) {
		int width	=mask3d[0].length;
		int height	=mask3d.length;
		int depth	=mask3d[0][0].length;
        ImageStack resultStack = new ImageStack(width, height);
        int pixels = width*height;
	
		
        for (int d = 0; d < depth; ++d) {
			/*Set Pixels*/
			byte[] slicePixels = new byte[width*height];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					if (mask3d[r][c][d] < 0.5){
						slicePixels[c+r*width] = 0;
					}else{
						slicePixels[c+r*width] = (byte) 0xff;
					}
				}
			}
			//impS
            resultStack.addSlice(null,slicePixels);

        }
		ImagePlus returnStack = new ImagePlus("Region", resultStack);
		/*Set Calibration*/
		returnStack.getCalibration().pixelWidth = calibration.pixelWidth;
		returnStack.getCalibration().pixelHeight  = calibration.pixelHeight;
		returnStack.getCalibration().pixelDepth  = calibration.pixelDepth;
		returnStack.getCalibration().setUnit("mm");
		//returnStack.setDisplayRange( 0, 1);
        return returnStack;
    }

    /*
      Get seed volume and maximum difference from user.
     
      @return <code>true</code> when user clicked OK (confirmed changes, <code>false</code>
              otherwise.
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
		gd.addCheckbox("3D", false);
		gd.addCheckbox("GrowUpDown", false);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return false;
        }
		seedPoints = new int[6];
		/*Get the values*/
		for (int i = 0; i<seedPoints.length;++i){
			seedPoints[i] = (int) gd.getNextNumber();
		}
        diffLimit	= gd.getNextNumber();
		threeD		= gd.getNextBoolean();
		growUpDown	= gd.getNextBoolean();
        return true;
    }
	
}
