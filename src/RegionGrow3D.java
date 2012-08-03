/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;
/*debugging*/
import ij.IJ;

/*3D region grow*/

public class RegionGrow3D extends RegionGrow{
	
	
	/*Parameters*/
	private double[][][] dataSlice;
	public byte[][][] segmentationMask;
	private double[] lbpModelHist;
	private double[][][] lbp3D;
	private int lbpBlockRadius;
	private LBP lbp;
	/*Global variables, saves effort in declaring functions...*/
	private int depthCount;
	private byte[][][] visited;

	/*Constructor for default maxDiff
		@param dataSlice		A 3D image stack, e.g. DICOM image stack
		@param segmentationMask	A 3D segmentation mask containing the seed points as 1 and others as 0	
	*/
	public RegionGrow3D(double[][][] dataSlice, byte[][][] segmentationMask){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = 250.0;
		//System.out.println("Constructor w/o");
		growRegion();
	}
	
	/*Constructor with maxDiff
		@param dataSlice		A 3D image stack, e.g. DICOM image stack
		@param segmentationMask	A 3D segmentation mask containing the seed points as 1 and others as 0
		@param maxDiff			The maximum difference from mean of points within segmentationMask to be grown to. The mask will grow to all pixels connected to the mask hat difer from the mean less than this value.
	*/
	public RegionGrow3D(double[][][] dataSlice, byte[][][] segmentationMask, double maxDiff){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		//System.out.println("Constructor w");
		growRegion();
	}
	
	/*Constructor for LBP
		@param dataSlice		A 3D image stack, e.g. DICOM image stack
		@param segmentationMask	A 3D segmentation mask containing the seed points as 1 and others as 0
		@param maxDiff			The maximum difference from mean of points within segmentationMask to be grown to. The mask will grow to all pixels connected to the mask that difer from the mean less than this value.
	*/
	public RegionGrow3D(double[][][] dataSlice, byte[][][] segmentationMask, double maxDiff,double[][][] lbp3D,LBP lbp,int lbpBlockRadius ,double[] lbpModelHist){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		this.lbp3D = lbp3D;
		this.lbp = lbp;
		setLBPModel(lbpModelHist);
		this.lbpBlockRadius = lbpBlockRadius;
		//System.out.println("Constructor w");
		growRegionLBP();
	}
	
	public void setLBPModel(double[] modelHist){
		lbpModelHist = new double[modelHist.length];
		for (int i = 0; i<modelHist.length;++i){
			lbpModelHist[i] = modelHist[i];
		}
	}
	
	private void growRegionLBP(){
		/*Init variables and add seed points to the queue*/
		pixelQueue	= new PriorityQueue<NextPixel>(1000000);
		columnCount	= dataSlice.length;
		rowCount	= dataSlice[0].length;
		depthCount	= dataSlice[0][0].length;
		visited		= new byte[columnCount][rowCount][depthCount];
			
		System.out.println("Start Init");
		/*Init pixelQueue*/
		int[][] seedIndices = find(segmentationMask);
		IJ.log("Number of seed points "+seedIndices.length);
		double[] lbpHist;
 		for (int i = 0; i<seedIndices.length; ++i){
			int[] coordinates = {seedIndices[i][0],seedIndices[i][1],seedIndices[i][2]};
			lbpHist = lbp.histc(lbp.reshape(lbp3D,coordinates[0]-lbpBlockRadius,coordinates[0]+lbpBlockRadius,coordinates[1]-lbpBlockRadius,coordinates[1]+lbpBlockRadius,coordinates[2],coordinates[2]));
			pixelQueue.add(new NextPixel(1.0-lbp.checkClose(lbpHist,lbpModelHist),coordinates));
		}
		
		/*Grow Region*/
		NextPixel nextPixel;
		long maskArea = seedIndices.length;
		int[][] neighbourhood = new int[6][3];
		int[] coordinates;
		while (pixelQueue.size() > 0){ //Go through all cells in queue
			nextPixel  =  pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			/*	Add 4-connected neighbourhood to the  queue, unless the
			neighbourhood pixels have already been visited or are part of the
			mask already		*/
			if (nextPixel.cost <= maxDiff){    //If cost is still more than maxDiff, LBP match is closer the higher the number...
				coordinates = nextPixel.coordinates;
				//System.out.println("r "+coordinates[0]+" c "+coordinates[1]);
				visited[coordinates[0]][coordinates[1]][coordinates[2]] = (byte) 1;
				if (segmentationMask[coordinates[0]][coordinates[1]][coordinates[2]] == 0){
					segmentationMask[coordinates[0]][coordinates[1]][coordinates[2]] = 1;
					++maskArea;	//Add the new pixel to the area
				}
				
				//Check 6-connected neighbour
				neighbourhood[0][0] = coordinates[0]-1;	/*Left one*/
				neighbourhood[1][0] = coordinates[0]+1;	/*Right one*/
				neighbourhood[2][0] = coordinates[0];
				neighbourhood[3][0] = coordinates[0];
				neighbourhood[4][0] = coordinates[0];
				neighbourhood[5][0] = coordinates[0];
				
				neighbourhood[0][1] = coordinates[1];
				neighbourhood[1][1] = coordinates[1];
				neighbourhood[2][1] = coordinates[1]-1;	/*Up one*/
				neighbourhood[3][1] = coordinates[1]+1;	/*Down one*/
				neighbourhood[4][1] = coordinates[1];
				neighbourhood[5][1] = coordinates[1];

				neighbourhood[0][2] = coordinates[2];	
				neighbourhood[1][2] = coordinates[2];	
				neighbourhood[2][2] = coordinates[2];
				neighbourhood[3][2] = coordinates[2];
				neighbourhood[4][2] = coordinates[2]-1;	/*Closer one*/
				neighbourhood[5][2] = coordinates[2]+1;	/*Further one*/
				//System.out.println("Qlength "+pixelQueue.size()+" mean "+currentMean+" alt Mean "+currentMean2+" area "+maskArea);
				checkNeighboursLBP(neighbourhood);
			}else{ //First pixel with higher than maxDiff cost or run out of pixels
				System.out.println("Break");
				break;
			}
        
		}
		
	}
	
	private void growRegion(){
		/*Init variables and add seed points to the queue*/
		pixelQueue	= new PriorityQueue<NextPixel>(1000000);
		columnCount	= dataSlice.length;
		rowCount	= dataSlice[0].length;
		depthCount	= dataSlice[0][0].length;
		visited		= new byte[columnCount][rowCount][depthCount];
		
		currentMean = getCurrentMean();
			
		System.out.println("Start Init");
		/*Init pixelQueue*/
		int[][] seedIndices = find(segmentationMask);

		for (int i = 0; i<seedIndices.length; ++i){
			int[] coordinates = {seedIndices[i][0],seedIndices[i][1],seedIndices[i][2]};
			pixelQueue.add(new NextPixel(Math.abs(dataSlice[seedIndices[i][0]][seedIndices[i][1]][seedIndices[i][2]]-currentMean),coordinates));
		}
		
		/*Grow Region*/
		NextPixel nextPixel;
		System.out.println("Start Growing");
		long maskArea = seedIndices.length;
		int[][] neighbourhood = new int[6][3];
		int[] coordinates;
		while (pixelQueue.size() > 0){ //Go through all cells in queue
			nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			/*	Add 4-connected neighbourhood to the  queue, unless the
			neighbourhood pixels have already been visited or are part of the
			mask already		*/
			if (nextPixel.cost <= maxDiff){    //If cost is still less than maxDiff
				coordinates = nextPixel.coordinates;
				//System.out.println("r "+coordinates[0]+" c "+coordinates[1]);
				visited[coordinates[0]][coordinates[1]][coordinates[2]] = (byte) 1;
				if (segmentationMask[coordinates[0]][coordinates[1]][coordinates[2]] == 0){
					segmentationMask[coordinates[0]][coordinates[1]][coordinates[2]] = 1;
					++maskArea;	//Add the new pixel to the area
					currentMean += ((dataSlice[coordinates[0]][coordinates[1]][coordinates[2]]-currentMean)/((double) maskArea)); //Adding the weighted difference updates the mean...
					//currentMean = getCurrentMean();  //The mean may be updated to include the new pixel. Might work just as well without update with several seeds...
				}
				
				
				
				//Check 4-connected neighbour
				neighbourhood[0][0] = coordinates[0]-1;	/*Left one*/
				neighbourhood[1][0] = coordinates[0]+1;	/*Right one*/
				neighbourhood[2][0] = coordinates[0];
				neighbourhood[3][0] = coordinates[0];
				neighbourhood[4][0] = coordinates[0];
				neighbourhood[5][0] = coordinates[0];
				
				neighbourhood[0][1] = coordinates[1];
				neighbourhood[1][1] = coordinates[1];
				neighbourhood[2][1] = coordinates[1]-1;	/*Up one*/
				neighbourhood[3][1] = coordinates[1]+1;	/*Down one*/
				neighbourhood[4][1] = coordinates[1];
				neighbourhood[5][1] = coordinates[1];

				neighbourhood[0][2] = coordinates[2];	
				neighbourhood[1][2] = coordinates[2];	
				neighbourhood[2][2] = coordinates[2];
				neighbourhood[3][2] = coordinates[2];
				neighbourhood[4][2] = coordinates[2]-1;	/*Closer one*/
				neighbourhood[5][2] = coordinates[2]+1;	/*Further one*/
				//System.out.println("Qlength "+pixelQueue.size()+" mean "+currentMean+" alt Mean "+currentMean2+" area "+maskArea);
				checkNeighbours(neighbourhood);
			}else{ //First pixel with higher than maxDiff cost or run out of pixels
				System.out.println("Break");
				break;
			}
        
		}
		
	}
	
	/*Update pixel queue LBP*/
	private void checkNeighboursLBP(int[][] neighbourhood){
		int[] coordinates;
		double[] lbpHist;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= lbpBlockRadius && coordinates[0] < columnCount-lbpBlockRadius && 
				coordinates[1] >=lbpBlockRadius && coordinates[1] <  rowCount-lbpBlockRadius &&
				coordinates[2] >=0 && coordinates[2] < depthCount){ //If the neigbour is within the image...
               if (visited[coordinates[0]][coordinates[1]][coordinates[2]] == (byte) 0 && segmentationMask[coordinates[0]][coordinates[1]][coordinates[2]] == 0){
					int[] queueCoordinates = {coordinates[0],coordinates[1],coordinates[2]};
					/*Calculate lbpHistogram and compare to modelHistogram*/
					lbpHist = lbp.histc(lbp.reshape(lbp3D,coordinates[0]-lbpBlockRadius,coordinates[0]+lbpBlockRadius,coordinates[1]-lbpBlockRadius,coordinates[1]+lbpBlockRadius,coordinates[2],coordinates[2]));
                  pixelQueue.add(new NextPixel(1.0-lbp.checkClose(lbpHist,lbpModelHist),queueCoordinates));
               }
            }
        }
	}
	
	
	/*Update pixel queue*/
	private void checkNeighbours(int[][] neighbourhood){
		int[] coordinates;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < columnCount && 
				coordinates[1] >=0 && coordinates[1] <  rowCount &&
				coordinates[2] >=0 && coordinates[2] < depthCount){ //If the neigbour is within the image...
               if (visited[coordinates[0]][coordinates[1]][coordinates[2]] == (byte) 0 && segmentationMask[coordinates[0]][coordinates[1]][coordinates[2]] == 0){
					int[] queueCoordinates = {coordinates[0],coordinates[1],coordinates[2]};
                  pixelQueue.add(new NextPixel(Math.abs(dataSlice[coordinates[0]][coordinates[1]][coordinates[2]]-currentMean),queueCoordinates));
               }
            }
        }
	}
	
	private int[][] find(byte[][][] matrix){
		int[][] temp = new int[matrix.length*matrix[0].length*matrix[0][0].length][3];
		int found = 0;
		for (int i = 0; i< matrix.length;++i){
			for (int j = 0; j< matrix[i].length;++j){
				for (int k = 0; k< matrix[i][j].length;++k){
					if (matrix[i][j][k] == 1){
						temp[found][0] = i;
						temp[found][1] = j;
						temp[found][2] = k;
						++found;					
					}
				}
			}
		}
		int[][] indices = new int[found][3];
		for (int i = 0; i<found; ++i){
			for (int j = 0; j< 3; ++j){
				indices[i][j] = temp[i][j];
			}
		}
		return indices;
	}
	
	private double getCurrentMean(){
		int[][] indices = find(segmentationMask);
		double sum = 0;
		for (int i = 0; i<indices.length; ++i){
			sum+= dataSlice[indices[i][0]][indices[i][1]][indices[i][2]];
		}
		sum/=((double) indices.length);
		return sum;
	}
	
	/*Test*/
	public static void main(String[] are){
		double[][][] image = {
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}}
						};
		byte[][][] mask = {
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,1,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,1,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,1,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}},
						{{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}}
						};
		RegionGrow3D rg = new RegionGrow3D(image, mask);
		rg.printGrown();
	}
	public void printGrown(){
		for (int d = 0; d <segmentationMask.length;++d){
			System.out.println("Slice "+d);
			for (int r = 0; r<segmentationMask[d].length;++r){
				for (int c = 0; c<segmentationMask[d][r].length;++c){
					System.out.print((int) segmentationMask[d][r][c]+"\t");
				}
				System.out.print("\n");
			}
			System.out.print("\n");
		}
		System.out.println("D "+segmentationMask.length+" R "+segmentationMask[0].length+" C "+segmentationMask[0][0].length);
	}
}








