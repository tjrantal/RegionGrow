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

public class IJGrowerT2 implements PlugIn {
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
					image3D[c][r][d] = (double) temp[c+r*width];
				}
			}
        }
		
		/*Construct the segmented mask*/
		byte[][][] segmentationMask = new byte[width][height][depth];	/*Initialized to zero by Java as default*/
		/*Create Seed volume, experimentally chosen....*/
		for (int d = seedPoints[4]; d < seedPoints[5]; ++d) {
			for (int r = seedPoints[2];r<seedPoints[3];++r){
				for (int c = seedPoints[0];c<seedPoints[1];++c){
					segmentationMask[c][r][d] = (byte) 1;
				}
			}
        }
		/*Grow stack*/
		if (threeD){	/*3D region grow*/
			RegionGrow3D r3d = new RegionGrow3D(image3D, segmentationMask, diffLimit);
			segmentationMask = r3d.segmentationMask;
		}else{			/*2D region grow*/
		
			segmentationMask = horizontalPlaneSegmentation(image3D,segmentationMask,3.0,0,1);
			/*Grow up down too*/
			if(growUpDown){
				IJ.log("UpDown");
				segmentationMask = frontalPlaneSegmentation(image3D,segmentationMask,3.0,0,0);
			/*Grow once more in sagittal direction*/
				if (secondGrow){
					
				}
				
			}
			
		}
		
		

		/*Dump out the results*/
		/*
		WriteMat writeMat = new WriteMat("C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\testDump.mat");
		writeMat.writeArray(image3D,"data");
		writeMat.writeArray(segmentationMask,"mask");
		writeMat.closeFile();
		*/
		
		
		/*Visualize result*/
		
		Calibration calibration = imp.getCalibration();
		
		double[] vRange = {imp.getDisplayRangeMin(),imp.getDisplayRangeMax()};
		//Visualize segmentation on the original image
		ImagePlus visualizationStack = createVisualizationStack(segmentationMask,image3D, calibration);
		visualizationStack.setDisplayRange(vRange[0],vRange[1]);
		visualizationStack.show();
		/*
		//Visualize segmentation on horizontal plane
		ImagePlus horizontalStack = createHorizontalVisualizationStack(segmentationMask,image3D, calibration);
		horizontalStack.setDisplayRange(vRange[0],vRange[1]);
		horizontalStack.show();
		*/
		//Visualize mask
        ImagePlus resultStack = createOutputStack(segmentationMask, calibration);
		resultStack.show();
		
    }
	
	/*Frontal plane analysis*/
	
	byte[][][] frontalPlaneSegmentation(double[][][] image3D, byte[][][] segmentationMask,double stdMultiplier,int preErodeReps, int postErodeReps){
		int width = image3D.length;
		int height = image3D[0].length;
		int depth = image3D[0][0].length;
		double[] meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		double[][] sliceData;
		byte[][] sliceMask;
		double stDev;
		boolean maskHasPixels;
		List threads = new ArrayList();
		/*Get diffLimit*/
		if (stdGrow){
			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			diffLimit = stdMultiplier*stDev;
		}
		IJ.log("Mean "+meanAndArea[0]+" DiffLimit "+diffLimit);
		for (int d = 0; d < depth; ++d) {
			/*Get the slice*/
			sliceData = new double[width][height];
			sliceMask = new byte[width][height];
			IJ.log("Slice "+d);
			maskHasPixels =false;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					sliceData[c][r] = image3D[c][r][d];
					sliceMask[c][r] = segmentationMask[c][r][d];
					if (sliceMask[c][r] ==1){
						maskHasPixels = true;
					}
				}
			}
			/*Run the region growing*/
			IJ.log("Slice "+d+" hasPixels "+maskHasPixels);
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				RegionGrow2D rg = new RegionGrow2D(sliceData,sliceMask,diffLimit,meanAndArea[0],(long) meanAndArea[1]);
				Thread newThread = new MultiThreader(rg,d,preErodeReps,postErodeReps);
				newThread.start();
				threads.add(newThread);
				IJ.log("Fired up thread "+threads.size());
			}
		}
		/*Wait for the threads to finish...*/
		for (int t = 0; t<threads.size();++t){
			try{
				((Thread) threads.get(t)).join();
			}catch(Exception er){}
			/*Copy the mask result to mask3D*/
			int d = ((MultiThreader) threads.get(t)).r;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreader) threads.get(t)).r2d.segmentationMask[c][r];
				}
			}
			IJ.log("Joined thread "+t+" of "+threads.size());
		}
		return segmentationMask;
	}
	
	/*Horizontal plane analysis*/
	byte[][][] horizontalPlaneSegmentation(double[][][] image3D, byte[][][] segmentationMask,double stdMultiplier,int preErodeReps, int postErodeReps){
		int width = image3D.length;
		int height = image3D[0].length;
		int depth = image3D[0][0].length;		
		double[] meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		double[][] sliceData;
		byte[][] sliceMask;
		double stDev;
		boolean maskHasPixels;
		List threads = new ArrayList();
		meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		/*Get diffLimit*/
		if (stdGrow){
			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			diffLimit = stdMultiplier*stDev;
		}
		IJ.log("Mean "+meanAndArea[0]+" DiffLimit "+diffLimit);
		/*Go through all of the slices*/
		threads.clear();
		for (int r = 0;r<height;++r){
			sliceData = new double[width][depth];
			sliceMask = new byte[width][depth];
			/*Get the slice*/
			maskHasPixels = false;
			for (int d = 0; d < depth; ++d) {
				for (int c = 0;c<width;++c){
					sliceData[c][d] = image3D[c][r][d];
					sliceMask[c][d] = segmentationMask[c][r][d];
					if (sliceMask[c][d] ==1){
						maskHasPixels = true;
					}
				}
			}
			/*Run the region growing*/
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				/*Try multithreading here*/
				RegionGrow2D rg = new RegionGrow2D(sliceData,sliceMask,diffLimit,meanAndArea[0],(long) meanAndArea[1]);
				Thread newThread = new MultiThreader(rg,r,preErodeReps,postErodeReps);
				newThread.start();
				threads.add(newThread);
			}
		}
		/*Wait for the threads to finish...*/
		for (int t = 0; t<threads.size();++t){
			try{
				((Thread) threads.get(t)).join();
			}catch(Exception er){}
			/*Copy the mask result to mask3D*/
			int r = ((MultiThreader) threads.get(t)).r;
			for (int d = 0; d < depth; ++d) {
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreader) threads.get(t)).r2d.segmentationMask[c][d];
				}
			}
		}
		return segmentationMask;
	}
	
	/*Sagittal plane analysis*/
	byte[][][] sagittalPlaneSegmentation(double[][][] image3D, byte[][][] segmentationMask,double stdMultiplier,int preErodeReps, int postErodeReps){
		int width = image3D.length;
		int height = image3D[0].length;
		int depth = image3D[0][0].length;		
		double[] meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		double[][] sliceData;
		byte[][] sliceMask;
		double stDev;
		boolean maskHasPixels;
		List threads = new ArrayList();
		meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		/*Get diffLimit*/
		if (stdGrow){
			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			diffLimit = stdMultiplier*stDev;
		}
		IJ.log("Mean "+meanAndArea[0]+" DiffLimit "+diffLimit);
		/*Go through all of the slices*/
		threads.clear();
		for (int c = 0;c<width;++c){
			sliceData = new double[height][depth];
			sliceMask = new byte[height][depth];
			/*Get the slice*/
			maskHasPixels = false;
			for (int d = 0; d < depth; ++d) {
				for (int r = 0;r<height;++r){
					sliceData[r][d] = image3D[c][r][d];
					sliceMask[r][d] = segmentationMask[c][r][d];
					if (sliceMask[r][d] ==1){
						maskHasPixels = true;
					}
				}
			}
			/*Run the region growing*/
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				/*Try multithreading here*/
				RegionGrow2D rg = new RegionGrow2D(sliceData,sliceMask,diffLimit,meanAndArea[0],(long) meanAndArea[1]);
				Thread newThread = new MultiThreader(rg,c,preErodeReps,postErodeReps);
				newThread.start();
				threads.add(newThread);
			}
		}
		/*Wait for the threads to finish...*/
		for (int t = 0; t<threads.size();++t){
			try{
				((Thread) threads.get(t)).join();
			}catch(Exception er){}
			/*Copy the mask result to mask3D*/
			int c = ((MultiThreader) threads.get(t)).r;
			for (int d = 0; d < depth; ++d) {
				for (int r = 0;r<height;++r){
					segmentationMask[c][r][d]=((MultiThreader) threads.get(t)).r2d.segmentationMask[r][d];
				}
			}
		}
		return segmentationMask;
	}
	
	/*Multithreading*/
	public class MultiThreader extends Thread{
		public RegionGrow2D r2d;
		public int r;
		private int postErodeReps;
		private int preErodeReps;
		private boolean preErode;
		/*Costructor*/
		public MultiThreader(RegionGrow2D r2d, int r,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			preErode = false;
		}
		/*Costructor with pre-erode*/
		public MultiThreader(RegionGrow2D r2d, int r,int preErodeReps,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.preErodeReps = preErodeReps;
			preErode = true;
		}
		
		public void run(){
			if (preErode){
				for (int i = 0;i<preErodeReps;++i){
					r2d.erodeMask();	//Remove extra stuff from sagittal growing...
				}
			}
			if (r2d.maskHasPixels()){
				r2d.growRegion();
				r2d.fillVoids(); //Fill void
				for (int i = 0; i<postErodeReps;++i){
					r2d.erodeMask();	/*Try to remove spurs...*/
				}
			}
		}
	}
	
	/*Horizontal image result*/
	private ImagePlus  createHorizontalVisualizationStack(byte[][][] mask3d,double[][][] data3d, Calibration calibration) {
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
					if (mask3d[c][r][d] == 0){
						tempSlice[c][d] = (short) (data3d[c][r][d]*0.5);
					}else{
						tempSlice[c][d] = (short) data3d[c][r][d];
					}
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
		ImagePlus returnStack = new ImagePlus("Visualization", resultStack);
		/*Set Calibration*/
		returnStack.getCalibration().pixelWidth = calibration.pixelWidth;
		returnStack.getCalibration().pixelHeight  = calibration.pixelWidth;//calibration.pixelDepth;
		returnStack.getCalibration().pixelDepth  = calibration.pixelHeight;
		returnStack.getCalibration().setUnit("mm");
		//returnStack.setDisplayRange( 0, 1);
        return returnStack;
	}
	
	/*Visual image result*/
	private ImagePlus createVisualizationStack(byte[][][] mask3d,double[][][] data3d, Calibration calibration) {
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
					if (mask3d[c][r][d] == 0){
						slicePixels[c+r*width] = (short) (data3d[c][r][d]*0.5);
					}else{
						slicePixels[c+r*width] = (short) data3d[c][r][d];
					}
				}
			}
			//impS
            resultStack.addSlice(null,slicePixels);

        }
		ImagePlus returnStack = new ImagePlus("Visualization", resultStack);
		/*Set Calibration*/
		returnStack.getCalibration().pixelWidth = calibration.pixelWidth;
		returnStack.getCalibration().pixelHeight  = calibration.pixelHeight;
		returnStack.getCalibration().pixelDepth  = 6.0;//calibration.pixelDepth;
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
		returnStack.getCalibration().pixelDepth  = 6.0;//calibration.pixelDepth;
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
		gd.addCheckbox("SecondGrow", false);
		gd.addCheckbox("StdGrow", false);		/*Use seed area 2*STDev as maxdiff*/

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
		secondGrow	= gd.getNextBoolean();
		stdGrow		= gd.getNextBoolean();
        return true;
    }
	
}
