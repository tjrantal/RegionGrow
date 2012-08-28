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
import ij.Prefs;				/*For finding out default path*/
import ij.io.FileSaver;			/*For saving result stack*/
import ij.io.FileOpener;			/*For saving result stack*/

/*
 Performs connected region growing. User is asked to provide the seed area points.
 Result is displayed as a binary image. Works with 3D images stack.
 */

public class IJGrowerLBPXCorrSeed3DSag implements PlugIn {
	private int[] seedPoints;
	private double diffLimit;
	private boolean threeD;
	private boolean growUpDown;
	private boolean secondGrow;
	private boolean stdGrow;
	/*Region grow parameters*/
	private double[] lbpLimits;
	private double[] greyLimits;
	private double[] gradientLimits;
	public String fileDump;
	public String visualDump;
	public String templatePath;
	public String templateFileName;
	public String templateMaskName;
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
		double[][][] lbp3D = new double[width][height][depth];	/*Initialized to zero by Java as default*/
		double[][][] gradient3D = new double[width][height][depth];	/*Initialized to zero by Java as default*/
		double[][] tempData;
		short[] temp;
		
		IJ.log("Start creating memory stacks");
        /*Create threads for slices*/
		List threads = new ArrayList();
		for (int d = 0; d < depth; ++d) {
            temp = (short[]) imageArrayPointers[d];
			tempData = new double[width][height];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					image3D[c][r][d] = (double) temp[c+r*width];
					tempData[c][r] = (double) temp[c+r*width];
				}
			}
			
			Thread newThread = new MultiThreaderLBPandGradient(tempData,d);
			newThread.start();
			threads.add(newThread);
			
			//IJ.log("Slice "+(d+1)+"/"+depth+" threading");
        }
		//Catch the slice threads
		
		for (int t = 0; t<threads.size();++t){
			try{
				((Thread) threads.get(t)).join();
			}catch(Exception er){}
			
			//Copy the mask result to mask3D
			int d = ((MultiThreaderLBPandGradient) threads.get(t)).d;
			//IJ.log("Slice "+(d+1)+"/"+depth+" finished");
			byte[][] lbpImage = ((MultiThreaderLBPandGradient) threads.get(t)).lbpImage;
			double[][] gradientImage = ((MultiThreaderLBPandGradient) threads.get(t)).gradientImage;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					lbp3D[c][r][d] =(double) lbpImage[c][r];
					gradient3D[c][r][d] = gradientImage[c][r];
				}
			}
		}
		
		
		
		
		
		IJ.log("Memory stacks done");
		//Read the template
		double[][] template2d = readTemplateFile(templatePath,templateFileName);
		double[][] mask2d = readTemplateFile(templatePath,templateMaskName);

	
		
		
		IJ.log("Starting 2D xcorr, might take a while");
		double[][][] xcorrelation3d = new double[image3D.length-template2d.length+1][image3D[0].length-template2d[0].length+1][depth];
		
		IJ.log("Start creating memory stacks");
        //Create threads for slices
		//List threads = new ArrayList();
		threads.clear();
		for (int d = 0; d < depth; ++d) {
			tempData = new double[width][height];
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					tempData[c][r] =  image3D[c][r][d];
				}
			}
			Thread newThread = new MultiThreaderXCorr(tempData,template2d,d);
			newThread.start();
			threads.add(newThread);

			IJ.log("Slice "+(d+1)+"/"+depth+" threading");
        }
		//Catch the slice threads
		for (int t = 0; t<threads.size();++t){
			try{
				((Thread) threads.get(t)).join();
			}catch(Exception er){}
			
			//Copy the mask result to mask3D
			int d = ((MultiThreaderXCorr) threads.get(t)).d;
			IJ.log("Slice "+(d+1)+"/"+depth+" finished");
			double[][] xcorrImage = ((MultiThreaderXCorr) threads.get(t)).xcorr;
			for (int r = 0;r<xcorrImage[0].length;++r){
				for (int c = 0;c<xcorrImage.length;++c){
					xcorrelation3d[c][r][d] =xcorrImage[c][r];
				}
			}
		}
		IJ.log("3D xcorr done");
		//Set seed according to XCorr
		Max max = RegionGrow.getMax(xcorrelation3d);
		IJ.log("X "+max.indices[0]+" Y "+max.indices[1]+" Z "+max.indices[2]);
		int[] maxIndices = checkMaxNeighbours(xcorrelation3d,max.indices);


		//Construct the segmented mask
		byte[][][] segmentationMask = new byte[width][height][depth];	//Initialized to zero by Java as default
		//Create Seed volume, experimentally chosen....
		for (int d = 0;d<maxIndices.length;++d){
			for (int c = 0;c<mask2d.length;++c){
				for (int r = 0;r<mask2d[c].length;++r){
					if (mask2d[c][r] > 0){
						segmentationMask[c+max.indices[0]-1][r+max.indices[1]-1][maxIndices[d]] = (byte) 1;
					}
				}
			}
		}
		
		double[] meanAndArea;
		
		//Test LBP Grow
		//Get LBP model histogram
		LBP lbp = new LBP(16,2);
		int lbpRadius = 7;
		double[] lbpModelHist = lbp.histc(LBP.reshape(lbp3D,segmentationMask));
		IJ.log("Starting LP grow");
		segmentationMask = frontalPlaneSegmentationLBP(lbp3D,segmentationMask,lbpLimits[0],lbp,lbpRadius,lbpModelHist,0,0);
		
		
		double stDev;
		double greySTD;
		meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
		double oldPixelNo = 1.0;
		double newPixelNo = meanAndArea[1];
		if (secondGrow){
			RegionGrow3D r3d;
			//3D grow to cover the whole bone
			while (newPixelNo/oldPixelNo > 1.01){ //Grow until less than 1% new pixels are added
				oldPixelNo = newPixelNo;
				lbpModelHist = lbp.histc(LBP.reshape(lbp3D,segmentationMask));
				meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
				stDev = RegionGrow.getStdev(segmentationMask, image3D,meanAndArea[0]);
				greySTD = 1.0*stDev;
				r3d = new RegionGrow3D(image3D, segmentationMask, lbpLimits[1],lbp3D,lbp,lbpRadius,lbpModelHist,meanAndArea[0],greySTD);
				segmentationMask = r3d.segmentationMask;
				segmentationMask = frontalPlaneSegmentationThree(image3D,gradient3D,segmentationMask,greyLimits[0],gradientLimits[0]);
				//segmentationMask = frontalPlaneSegmentation(image3D,segmentationMask,growLimits[1],0,0);
				meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
				newPixelNo = meanAndArea[1];
				System.out.println("Pixels in Mask after "+meanAndArea[1]+" Increment "+newPixelNo/oldPixelNo);
			}
			
			
			//Sagittal grow to get close to bone borders...
			
			oldPixelNo = 1;
			IJ.log("Into frontal plane three");
			int growReps = 0;
			while (newPixelNo/oldPixelNo > 1.01 && growReps < 8){ //Grow until less than 1% new pixels are added
				oldPixelNo = newPixelNo;
				meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
				segmentationMask = frontalPlaneSegmentationThree(image3D,gradient3D,segmentationMask,greyLimits[1],gradientLimits[1]);
				meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
				newPixelNo = meanAndArea[1];
				++growReps;
				System.out.println("Rep "+growReps+" Pixels in Mask after Sagittal "+meanAndArea[1]+" Increment "+newPixelNo/oldPixelNo);
			}
			
			/*
			//dilate masks
			meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
			//double[] dilateLimits = {meanAndArea[0]-500.0,meanAndArea[0]+500.0};
			double[] dilateLimits = {500.0,1500.0};
			IJ.log("Dilate Limits "+dilateLimits[0]+" up "+dilateLimits[1]);
			oldPixelNo = (double) meanAndArea[1];
			for (int i = 0; i<20;++i){
				
				segmentationMask = RegionGrow.dilateSlices(segmentationMask,image3D,dilateLimits);
				meanAndArea = RegionGrow.getCurrentMeanAndArea(segmentationMask, image3D);
				System.out.println("Pixels afer grow "+meanAndArea[1]+" Increment "+(((double)meanAndArea[1])/oldPixelNo) );
				oldPixelNo = (double)meanAndArea[1];
			}
			*/
		}
		
		
		

		/*
		//Dump out the results
		IJ.log("Starting File Dump");
		WriteMat writeMat = new WriteMat(fileDump);
		writeMat.writeArray(image3D,"data");
		writeMat.writeArray(segmentationMask,"mask");
		writeMat.closeFile();
		IJ.log("File Dump done");
		*/
		
		//Visualize result
		
		Calibration calibration = imp.getCalibration();
		double[] vRange = {imp.getDisplayRangeMin(),imp.getDisplayRangeMax()};
		//Visualize segmentation on the original image
		ImagePlus visualizationStack = createVisualizationStack(segmentationMask,image3D, calibration);
		visualizationStack.setDisplayRange(vRange[0],vRange[1]);
		visualizationStack.show();
		
		
		FileSaver fsaver = new FileSaver(visualizationStack);
        fsaver.saveAsRawStack(visualDump);
    }
	
	/*Check neigbouring slice xcorrs*/
	public int[] checkMaxNeighbours(double[][][] xcorrelation3D,int[] indices){
		int init= 0;
		Vector<Integer> neighbours = new Vector<Integer>();
		neighbours.add(indices[2]);
		int end = xcorrelation3D[0][0].length;
		if (indices[2]>1){init = indices[2]-2;}
		if (indices[2] < end+3){end = indices[2]+2;}
		int[] check = {-1,1,-2,2};
		for (int i = 0;i<check.length;++i){
			if (indices[2]+check[i] > 0 && indices[2]+check[i] < xcorrelation3D[0][0].length){
				if (xcorrelation3D[indices[0]][indices[1]][indices[2]+check[i]] >=0.95*xcorrelation3D[indices[0]][indices[1]][indices[2]]){
					neighbours.add(indices[2]+check[i]);
				}
				if (neighbours.size() > 2){
					break;
				}
			}
		}
		int[] returnValue = new int[neighbours.size()];
		for (int i = 0; i<returnValue.length;++i){
			returnValue[i] = neighbours.get(i);
		}
		return returnValue;
	}
	
	/*Read template data*/
	public double[][] readTemplateFile(String templatePath, String templateFileName){
			FileInfo fi = new FileInfo();
		fi.width = seedPoints[0];
		fi.height = seedPoints[1];
		fi.offset = 0;
		fi.fileFormat = FileInfo.RAW;
		fi.fileType = FileInfo.GRAY16_SIGNED;
		fi.intelByteOrder = true;
		fi.fileName = templateFileName;
		fi.directory = templatePath;
		FileOpener fopen = new FileOpener(fi);
		ImagePlus templateImage = fopen.open(false);
		double [][] template2d = new double[templateImage.getWidth()][templateImage.getHeight()];
		short[] templatePixels;
		
		templatePixels = (short[]) templateImage.getProcessor().getPixels();
		int subt = 0;
		subt = 1<<15;
		//System.out.println(subt);
		for (int r = 0;r<template2d[0].length;++r){
			for (int c = 0;c<template2d.length;++c){
				template2d[c][r] = ((double) (((int) templatePixels[c+r*template2d.length])+subt));
			}
		}
		templateImage.close();
		return template2d;
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
	
	byte[][][] frontalPlaneSegmentationThree(double[][][] image3D, double[][][] gradient3D,byte[][][] segmentationMask,double stdMultiplier,double gradientMultiplier){
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
			
			/*Limit greyLimit to 500 and diffLimit to 150*/
			greyLimit = greyLimit < 500.0 ? greyLimit : 500.0;
			diffLimitGradient = diffLimitGradient < 150.0 ? diffLimitGradient : 150.0;
			
		IJ.log("Mean "+meanAndArea[0]+" GreyLimit "+greyLimit+" GMean "+meanAndAreaGradient[0]+" GLimit "+diffLimitGradient);
		for (int d = 0; d < depth; ++d) {
			/*Get the slice*/
			sliceData = new double[width][height];
			sliceMask = new byte[width][height];
			gradientData= new double[width][height];
			maskHasPixels =false;
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					if (segmentationMask[c][r][d] ==1){
						maskHasPixels = true;
						break;
					}
				}
				if(maskHasPixels){break;}
			}
			//IJ.log("MaskHasPixels "+maskHasPixels+" "+d);
			/*Run the region growing*/
			if (maskHasPixels){ /*Do the remaining steps only if a pixel existed within the slice...*/
				RegionGrow2D3DNeighbourhood rg = new RegionGrow2D3DNeighbourhood(image3D,gradient3D,segmentationMask,d,diffLimit,greyLimit,meanAndArea[0],diffLimitGradient,meanAndAreaGradient[0],(long) meanAndArea[1]);
				Thread newThread = new MultiThreader3Dnh(rg,d);
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
			int d = ((MultiThreader3Dnh) threads.get(t)).r;
			//IJ.log("Caught slice "+d);
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					segmentationMask[c][r][d]=((MultiThreader3Dnh) threads.get(t)).r2d.segmentationMask[c][r][d];
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
	
	
    /*
      Get seed volume and maximum difference from user.
     
      @return <code>true</code> when user clicked OK (confirmed changes, <code>false</code>
              otherwise.
     */
	 
    private boolean getParameters() {	
		/*Create dialog*/
        final GenericDialog gd = new GenericDialog("Grow options");
        gd.addMessage("Template for seed search (raw 16 bit signed int)");
		//gd.addStringField("PathToTemplate",Prefs.getDefaultDirectory()+"/",60);
		gd.addStringField("PathToTemplate","C:/MyTemp/oma/Timon/tyo/SubchondralPilot/ijGrower/src/template/",60);
		
		gd.addStringField("TemplateFileName","master.raw",60);
		gd.addStringField("TemplateMaskName","mask.raw",60);
        gd.addNumericField("Width", 169, 0);
		gd.addNumericField("Height", 250, 0);
        gd.addMessage("Maximum difference");
        gd.addNumericField("maxDiff", 5.0, 1);
		gd.addCheckbox("3D", false);
		gd.addCheckbox("GrowUpDown", false);
		gd.addCheckbox("SecondGrow", false);
		gd.addCheckbox("StdGrow", false);		/*Use seed area 2*STDev as maxdiff*/
		gd.addNumericField("LBPlimit1", 0.11, 3);
		gd.addNumericField("LBPlimit2", 0.12, 3);
        gd.addNumericField("GreyLimit1", 1.0, 1);
		gd.addNumericField("GreyLimit2", 2.0, 1);
		gd.addNumericField("GreyLimit3", 2.0, 1);
		gd.addNumericField("GradientLimit", 1.5, 1);
		gd.addNumericField("GradientLimit2", 1.5, 1);
		gd.addNumericField("GradientLimit3", 1.5, 1);
		gd.addStringField("Result_dump_path",Prefs.getDefaultDirectory(),60);
		gd.addStringField("Visual_result_path",Prefs.getDefaultDirectory(),60);
        gd.showDialog();

        if (gd.wasCanceled()) {
            return false;
        }
		templatePath = gd.getNextString();
		templateFileName = gd.getNextString();
		templateMaskName = gd.getNextString();
		seedPoints = new int[2];
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
		lbpLimits = new double[2];
		for (int i = 0; i<lbpLimits.length;++i){
			lbpLimits[i] = gd.getNextNumber();
		}
		greyLimits = new double[3];
		for (int i = 0; i<greyLimits.length;++i){
			greyLimits[i] = gd.getNextNumber();
		}
		gradientLimits = new double[3];
		for (int i = 0; i<gradientLimits.length;++i){
			gradientLimits[i] = gd.getNextNumber();
		}
		
		fileDump = gd.getNextString();
		visualDump =gd.getNextString();
        return true;
    }
	
}
