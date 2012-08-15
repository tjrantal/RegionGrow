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

public class IJ3DCorr implements PlugIn {
	private int[] seedPoints;
	private double diffLimit;
	private boolean threeD;
	private boolean growUpDown;
	private boolean secondGrow;
	private boolean stdGrow;
	/*Region grow parameters*/
	private double[] growLimits;
	
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
		
		/*Construct 3D image and 3D lbp memory stacks*/
        double [][][] image3D = new double[width][height][depth];

		
		IJ.log("Start creating memory stacks");
        /*Create threads for slices*/
		short[] temp;
		for (int d = 0; d < depth; ++d) {
            temp = (short[]) imageArrayPointers[d];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					image3D[c][r][d] = (double) temp[c+r*width];
				}
			}
        }
		
		IJ.log("Memory stacks done");
		
		double[][][] template3d = new double[120][140][9];
		/*Create template cylinder...*/
		double radius = 0;
		for (int i = 0;i<template3d.length;++i){
			for (int j = 0;j<120;++j){
				for (int k = 0;k<template3d[i][j].length;++k){
					radius = ((double) j)/120.0*25.0+25.0;
					if (Math.sqrt((((double) i)-60.0)*(((double) i)-60.0)+((((double) k)-4)*13.1)*((((double) k)-4)*13.1)) < radius){
						template3d[i][139-j][k] = 1;
					}
				}
			}
		}
		
		IJ.log("Template bone done");
		
		IJ.log("Starting 3D xcorr, might take a while");
		double[][][] xcorrelation3d = Filters.xcorr(image3D,template3d);
		IJ.log("3D xcorr done");
		Calibration calibration = imp.getCalibration();
		double[] vRange = {imp.getDisplayRangeMin(),imp.getDisplayRangeMax()};
		//Visualize segmentation on the original image
		
		ImagePlus resultStack = createTemplateStack(template3d, calibration);
		resultStack.show();
		
		ImagePlus xcorrelationStack = createXCorrStack(xcorrelation3d, calibration);
		xcorrelationStack.show();
		
    }
	
	/*Frontal plane LBP analysis*/	
		byte[][][] frontalPlaneSegmentationLBP(double[][][] image3D, byte[][][] segmentationMask,double diffLimit, LBP lbp, int lbpBlockRadius, double[] lbpModelHist,int preErodeReps, int postErodeReps){
		int width = image3D.length;
		int height = image3D[0].length;
		int depth = image3D[0][0].length;
		double[][] sliceData;
		byte[][] sliceMask;
		boolean maskHasPixels;
		List threads = new ArrayList();
		/*Get diffLimit*/
		for (int d = 0; d < depth; ++d) {
			/*Get the slice*/
			sliceData = new double[width][height];
			sliceMask = new byte[width][height];
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
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				RegionGrow2D rg = new RegionGrow2D(sliceData,sliceMask,diffLimit,lbp,lbpBlockRadius,lbpModelHist);
				Thread newThread = new MultiThreaderLBP2D(rg,d,preErodeReps,postErodeReps);
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
			int d = ((MultiThreaderLBP2D) threads.get(t)).r;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreaderLBP2D) threads.get(t)).r2d.segmentationMask[c][r];
				}
			}
		}
		return segmentationMask;
	}
	
	/*Frontal plane analysis with gradientimag*/
	
	byte[][][] frontalPlaneSegmentationTwo(double[][][] image3D, double[][][] gradient3D,byte[][][] segmentationMask,double stdMultiplier,double gradientMultiplier,int preErodeReps, int postErodeReps){
		int width = image3D.length;
		int height = image3D[0].length;
		int depth = image3D[0][0].length;
		double[] meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		double[] meanAndAreaGradient = RegionGrow.getCurrentMeanAndArea(segmentationMask, gradient3D);
		double[][] sliceData;
		double[][] gradientData;
		byte[][] sliceMask;
		double stDev;
		double greyLimit;
		double diffLimitGradient = 0;
		boolean maskHasPixels;
		List threads = new ArrayList();
		/*Get diffLimit*/

			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			greyLimit = stdMultiplier*stDev;
			double stDevGradient = RegionGrow.getStdev(segmentationMask, gradient3D,meanAndAreaGradient[0]);
			diffLimitGradient = stDevGradient*gradientMultiplier;//*stdMultiplier;

		IJ.log("Mean "+meanAndArea[0]+" GreyLimit "+greyLimit+" GMean "+meanAndAreaGradient[0]+" GLimit "+diffLimitGradient);
		for (int d = 0; d < depth; ++d) {
			/*Get the slice*/
			sliceData = new double[width][height];
			sliceMask = new byte[width][height];
			gradientData= new double[width][height];
			maskHasPixels =false;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					sliceData[c][r] = image3D[c][r][d];
					sliceMask[c][r] = segmentationMask[c][r][d];
					gradientData[c][r] = gradient3D[c][r][d];
					if (sliceMask[c][r] ==1){
						maskHasPixels = true;
					}
				}
			}
			/*Run the region growing*/
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				RegionGrow2Dgradient rg = new RegionGrow2Dgradient(sliceData,gradientData,sliceMask,diffLimit,greyLimit,meanAndArea[0],diffLimitGradient,meanAndAreaGradient[0],(long) meanAndArea[1]);
				Thread newThread = new MultiThreaderGradient(rg,d,preErodeReps,postErodeReps);
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
			int d = ((MultiThreaderGradient) threads.get(t)).r;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreaderGradient) threads.get(t)).r2d.segmentationMask[c][r];
				}
			}
		}
		return segmentationMask;
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
		
			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			double diffLimit = stdMultiplier*stDev;
		
		IJ.log("Mean "+meanAndArea[0]+" DiffLimit "+diffLimit);
		for (int d = 0; d < depth; ++d) {
			/*Get the slice*/
			sliceData = new double[width][height];
			sliceMask = new byte[width][height];
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
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				RegionGrow2D rg = new RegionGrow2D(sliceData,sliceMask,diffLimit,meanAndArea[0],(long) meanAndArea[1]);
				Thread newThread = new MultiThreader(rg,d,preErodeReps,postErodeReps);
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
			int d = ((MultiThreader) threads.get(t)).r;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreader) threads.get(t)).r2d.segmentationMask[c][r];
				}
			}
		}
		return segmentationMask;
	}

	/*Horizontal plane analysis*/
	byte[][][] horizontalPlaneSegmentationLBP(double[][][] image3D, byte[][][] segmentationMask,double diffLimit, LBP lbp, int lbpBlockRadius, double[] lbpModelHist,int preErodeReps, int postErodeReps){
		int width = image3D.length;
		int height = image3D[0].length;
		int depth = image3D[0][0].length;		
		double[][] sliceData;
		byte[][] sliceMask;
		double stDev;
		boolean maskHasPixels;
		List threads = new ArrayList();
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
				RegionGrow2D rg = new RegionGrow2D(sliceData,sliceMask,diffLimit,lbp,lbpBlockRadius,lbpModelHist);
				Thread newThread = new MultiThreaderLBP2D(rg,r,preErodeReps,postErodeReps);
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
			int r = ((MultiThreaderLBP2D) threads.get(t)).r;
			for (int d = 0; d < depth; ++d) {
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreaderLBP2D) threads.get(t)).r2d.segmentationMask[c][d];
				}
			}
		}
		return segmentationMask;
	}
	
	/*Horizontal plane analysis*/
	byte[][][] horizontalPlaneSegmentation(double[][][] image3D, byte[][][] segmentationMask,double stdMultiplier,int preErodeReps, int postErodeReps, boolean doFillVoids){
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
		
			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			double diffLimit = stdMultiplier*stDev;
		
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
				Thread newThread = new MultiThreader(rg,r,preErodeReps,postErodeReps,doFillVoids);
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
		
			stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
			double diffLimit = stdMultiplier*stDev;
		
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
		returnStack.getCalibration().pixelDepth  = calibration.pixelDepth;
		returnStack.getCalibration().setUnit("mm");
		//returnStack.setDisplayRange( 0, 1);
        return returnStack;
	}

	/*Visual mask result*/
	private ImagePlus createTemplateStack(double[][][] mask3d, Calibration calibration) {
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
	
	
	
		/*Visual mask result*/
	private ImagePlus createXCorrStack(double[][][] mask3d, Calibration calibration) {
		int width	=mask3d.length;
		int height	=mask3d[0].length;
		int depth	=mask3d[0][0].length;
        ImageStack resultStack = new ImageStack(width, height);
        int pixels = width*height;
	
		float min = Float.POSITIVE_INFINITY;
		float max = Float.NEGATIVE_INFINITY;
        for (int d = 0; d < depth; ++d) {
			/*Set Pixels*/
			float[] slicePixels = new float[width*height];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					slicePixels[c+r*width] = (float) mask3d[c][r][d];
					if ((float) mask3d[c][r][d] > max){max = (float) mask3d[c][r][d];}
					if ((float) mask3d[c][r][d] < min){min = (float) mask3d[c][r][d];}
				}
			}
			//impS
            resultStack.addSlice(null,slicePixels);

        }
		ImagePlus returnStack = new ImagePlus("XCorr", resultStack);
		/*Set Calibration*/
		returnStack.getCalibration().pixelWidth = calibration.pixelWidth;
		returnStack.getCalibration().pixelHeight  = calibration.pixelHeight;
		returnStack.getCalibration().pixelDepth  = calibration.pixelDepth;
		returnStack.getCalibration().setUnit("mm");
		returnStack.setDisplayRange( min, max);
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
        gd.addNumericField("maxDiff", 5.0, 1);
		gd.addCheckbox("3D", false);
		gd.addCheckbox("GrowUpDown", false);
		gd.addCheckbox("SecondGrow", false);
		gd.addCheckbox("StdGrow", false);		/*Use seed area 2*STDev as maxdiff*/
		gd.addNumericField("LBPlimit", 0.12, 3);
        gd.addNumericField("GreyLimit1", 1.0, 1);
		gd.addNumericField("GreyLimit2", 2.0, 1);
		gd.addNumericField("GradientLimit", 2.0, 1);

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
		/*Get region grow parameters*/
		growLimits = new double[4];
		for (int i = 0; i<growLimits.length;++i){
			growLimits[i] = gd.getNextNumber();
		}
        return true;
    }
	
}
