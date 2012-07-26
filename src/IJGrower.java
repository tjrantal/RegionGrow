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
		
		/*Check image properties*/
		IJ.log("Start acquiring properties");
		Properties properties = imp.getProperties();
		String[] props = (String[]) properties.stringPropertyNames().toArray();
		IJ.log("Got properties");
		for (int i = 0;i<props.length;++i){
			IJ.log(props[i]);
		}
       

		/*Construct the segmented mask*/
		Calibration calibration = imp.getCalibration();
		FileInfo fi = new FileInfo();
		try { fi = imp.getOriginalFileInfo();}
		catch (NullPointerException npe){IJ.error("Couldn't get fileInfo");} 

		
		
		double [][][] mask3D = new double[width][height][depth];	/*Initialized to zero by Java as default*/
		/*Create Seed volume, experimentally chosen....*/
		for (int d = seedPoints[4]; d < seedPoints[5]; ++d) {
			for (int r = seedPoints[2];r<seedPoints[3];++r){
				for (int c = seedPoints[0];c<seedPoints[1];++c){
					mask3D[r][c][d] = (double) 1;
				}
			}
        }
		/*Grow stack*/
		RegionGrow3D r3d = new RegionGrow3D(image3D, mask3D, diffLimit);
		/*Visualize result*/
        ImageStack resultStack = createOutputStack(r3d.segmentationMask, calibration,fi);
		//resultStack.update(imp.getProcessor());
        new ImagePlus("Region", resultStack).show();
    }
	
	/*Visual mask result*/
	private ImageStack createOutputStack(double[][][] mask3d, Calibration calibration,FileInfo fi) {
		int width	=mask3d[0].length;
		int height	=mask3d.length;
		int depth	=mask3d[0][0].length;
        ImageStack resultStack = new ImageStack(width, height);
        int pixels = width*height;
		
		/*Set stack image dimensions according to the original dimensions...*/
		/*
		IJ.log("XUnit "+calibration.getXUnit());
		IJ.log("XUnit "+calibration.getYUnit());
		IJ.log("XUnit "+calibration.getZUnit());
		*/
		IJ.log("FI pixelW "+fi.pixelWidth);
		IJ.log("FI pixelH "+fi.pixelHeight);
		IJ.log("FI pixelD "+fi.pixelDepth);
		IJ.log("FI W "+fi.width);
		IJ.log("FI H "+fi.height);
		
		/*
		Calibration stackCal = new Calibration();
		stackCal.setXUnit(calibration.getXUnit());
		stackCal.setYUnit(calibration.getYUnit());
		stackCal.setZUnit(calibration.getZUnit());
		*/
		FileInfo fiS = new FileInfo();
		fiS.pixelWidth = fi.pixelWidth;
		fiS.pixelHeight = fi.pixelHeight;
		fiS.pixelDepth = fi.pixelDepth;
		fiS.width = fi.width;
		fiS.height = fi.height;
		fiS.valueUnit = "mm";
		fiS.fileFormat = fiS.RAW;
		fiS.compression = fiS.COMPRESSION_NONE;
		fiS.fileType = fiS.GRAY8;	//
        /*Create file info string for properties*/
		String[] propertyNames = {"Pixel Width","Pixel Height","Voxel Depth"};
		String[] propertyValues = {Double.toString(fi.pixelWidth),Double.toString(fi.pixelHeight),Double.toString(fi.pixelDepth)};
		String properties = new String();
		for (int i = 0;i<propertyNames.length;++i){
			properties += propertyNames[i]+": "+propertyValues[i]+"\n";
		}
		fiS.info = properties;
		
		
		
        for (int d = 0; d < depth; ++d) {
			ImagePlus impS = NewImage.createByteImage("Stack "+d,width,height,1,NewImage.FILL_BLACK);
			//impS.setCalibration(stackCal);
			impS.setFileInfo(fiS);
			impS.setProperty("Info", properties);
            byte[] slicePixels = (byte[]) impS.getProcessor().getPixels();
			for (int r = 0;r<height;++r){
				for (int c = 0;c<width;++c){
					slicePixels[c+r*width] = (byte) (mask3d[r][c][d]*127.0);
				}
			}
            resultStack.addSlice(impS.getProcessor());
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
