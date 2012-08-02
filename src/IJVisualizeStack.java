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

public class IJVisualizeStack implements PlugIn {
	private int[] seedPoints;
	private double diffLimit;
	private boolean threeD;
	private boolean growUpDown;
	private boolean secondGrow;
	private boolean stdGrow;
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
		
		ImageStack stack = imp.getStack();
		final Object[] imageArrayPointers = stack.getImageArray();
		
		/*Construct a 3D memory stack*/
        double [][][] image3D = new double[width][height][depth];
		short[] temp;
        for (int d = 0; d < depth; ++d) {
            temp = (short[]) imageArrayPointers[d];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					image3D[c][r][d] = (double) temp[c+r*width];
				}
			}
        }
		
		/*Construct lbp image Stack*/
		double[][][] lbp3D = new double[width][height][depth];	/*Initialized to zero by Java as default*/
		double[][] tempData = new double[width][height];
		LBP lbp = new LBP(16,2);
		for (int d = 0; d < depth; ++d) {
            temp = (short[]) imageArrayPointers[d];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					tempData[c][r] = image3D[c][r][d];
				}
			}
			byte[][] lbpImage = lbp.getLBP(tempData);
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					lbp3D[c][r][d] = (double) lbpImage[c][r];
				}
			}
        }
		
		/*Construct lbp image stack on the other direction...*/
		double[][][] lbpHorizonal3D = new double[width][height][depth];	/*Initialized to zero by Java as default*/
		tempData = new double[width][depth];
		IJ.log("Start getting horizontal LBP image");
		lbp = new LBP(8,1);
		for (int r = 0;r<height;++r){
			for (int d = 0; d < depth; ++d) {
				for (int c = 0;c<width;++c){
					tempData[c][d] = image3D[c][r][d];
				}
			}
			byte[][] lbpImage = lbp.getLBP(tempData);
			for (int d = 0; d < depth; ++d) {
				for (int c = 0;c<width;++c){
					lbpHorizonal3D[c][r][d] = (double) lbpImage[c][d];
				}
			}
        }
		IJ.log("Got horizontal LBP image");
		
		/*Visualize result*/
		Calibration calibration = imp.getCalibration();
		double[] vRange = {imp.getDisplayRangeMin(),imp.getDisplayRangeMax()};
		/*Visualize segmentation on the original image*/
		ImagePlus visualizationStack = createVisualizationStack(lbp3D, calibration);
		visualizationStack.setDisplayRange(0,17);
		visualizationStack.show();
		/*Visualize segmentation on horizontal plane*/
		ImagePlus horizontalStack = createHorizontalVisualizationStack(lbpHorizonal3D, calibration);
		horizontalStack.setDisplayRange(0,9);
		horizontalStack.show();
    }

	/*Horizontal image result*/
	private ImagePlus  createHorizontalVisualizationStack(double[][][] mask3d, Calibration calibration) {
		int width	=mask3d.length;
		int height	=mask3d[0].length;
		int depth	=mask3d[0][0].length;
		int aspectRatioCorrection = (int) (calibration.pixelDepth/calibration.pixelWidth);
        ImageStack resultStack = new ImageStack(width, depth*aspectRatioCorrection);
		double[][] tempSlice = new double[width][depth];
		for (int r = 0;r<height;++r){
			/*Set Pixels*/
			
			for (int d = 0; d < depth; ++d) {
				for (int c = 0;c<width;++c){
					tempSlice[c][d] = mask3d[c][r][d];
				}
			}
			/*ResizeImage*/
			short[] slicePixels = new short[width*depth*aspectRatioCorrection];
			for (int d = 0; d < depth*aspectRatioCorrection; ++d) {
				for (int c = 0;c<width;++c){
					slicePixels[c+d*width] = (short) (Filters.getBicubicInterpolatedPixel((double) c, ((double) d)/((double)aspectRatioCorrection), tempSlice));
				}
			}
			
			//impS
            resultStack.addSlice(null,slicePixels);

        }
		ImagePlus returnStack = new ImagePlus("Horizontal", resultStack);
		/*Set Calibration*/
		returnStack.getCalibration().pixelWidth = calibration.pixelWidth;
		returnStack.getCalibration().pixelHeight  = calibration.pixelWidth;//calibration.pixelDepth;
		returnStack.getCalibration().pixelDepth  = calibration.pixelHeight;
		returnStack.getCalibration().setUnit("mm");
		//returnStack.setDisplayRange( 0, 1);
        return returnStack;
	}
	
	/*Visual image result*/
	private ImagePlus createVisualizationStack(double[][][] mask3d, Calibration calibration) {
		int width	=mask3d.length;
		int height	=mask3d[0].length;
		int depth	=mask3d[0][0].length;
        ImageStack resultStack = new ImageStack(width, height);
        int pixels = width*height;
	
		
        for (int d = 0; d < depth; ++d) {
			/*Set Pixels*/
			short[] slicePixels = new short[width*height];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
						slicePixels[c+r*width] = (short) mask3d[c][r][d];
				}
			}
			//impS
            resultStack.addSlice(null,slicePixels);

        }
		ImagePlus returnStack = new ImagePlus("Sagittal", resultStack);
		/*Set Calibration*/
		returnStack.getCalibration().pixelWidth = calibration.pixelWidth;
		returnStack.getCalibration().pixelHeight  = calibration.pixelHeight;
		returnStack.getCalibration().pixelDepth  = calibration.pixelDepth;
		returnStack.getCalibration().setUnit("mm");
		//returnStack.setDisplayRange( 0, 1);
        return returnStack;
	}
	
	/*Visual mask result*/
	private ImagePlus createOutputStack(byte[][][] mask3d, Calibration calibration) {
		int width	=mask3d.length;
		int height	=mask3d[0].length;
		int depth	=mask3d[0][0].length;
        ImageStack resultStack = new ImageStack(width, height);
        int pixels = width*height;
	
		
        for (int d = 0; d < depth; ++d) {
			/*Set Pixels*/
			byte[] slicePixels = new byte[width*height];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					if (mask3d[c][r][d] == 0){
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
	
}
