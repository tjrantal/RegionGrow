/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;
import java.util.Vector;

public class RegionGrow2D extends RegionGrow{
	
	
	/*Parameters*/
	protected double[][] dataSlice;
	public byte[][] segmentationMask;
	private double[][] lbp2D;
	
	/*Global variables, saves effort in declaring functions...*/
	protected byte[][] visited;

	/*Constructor for default maxDiff*/
	public RegionGrow2D(double[][] dataSlice, byte[][] segmentationMask){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = 250.0;
		currentMean = getCurrentMean();
	}
	
	/*Constructor with maxDiff*/
	public RegionGrow2D(double[][] dataSlice, byte[][] segmentationMask, double maxDiff){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		currentMean = getCurrentMean();
	}
	
	/*Constructor with maxDiff, mean and area*/
	public RegionGrow2D(double[][] dataSlice, byte[][] segmentationMask, double maxDiff, double currentMean, long maskArea){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		this.currentMean = currentMean;
		this.maskArea = maskArea;
	}
	
	/*Constructor for LBP*/
	public RegionGrow2D(double[][] dataSlice, byte[][] segmentationMask, double maxDiff,LBP lbp,int lbpBlockRadius ,double[] lbpModelHist){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		this.lbp2D = dataSlice;
		this.lbp = lbp;
		setLBPModel(lbpModelHist);
		this.lbpBlockRadius = lbpBlockRadius;
	}
	
	
	public boolean maskHasPixels(){
		for (int i=0; i<segmentationMask.length; i++){
			for (int j=0; j<segmentationMask[i].length; j++){
				if (segmentationMask[i][j] > 0){
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean growRegionLBP(){
		/*Init variables and add seed points to the queue*/
		rowCount = lbp2D[0].length;
		columnCount = lbp2D.length;
		pixelQueue = new PriorityQueue<NextPixel>();	/*Try to reserve memory to enable faster execution...*/
		
		visited = new byte[columnCount][rowCount];

		/*Init pixelQueue*/
		int[][] seedIndices = find(segmentationMask);
		if (seedIndices == null){return false;}
		double[] lbpHist;
		for (int i = 0; i<seedIndices.length; ++i){
			if (seedIndices[i][0] >= lbpBlockRadius && seedIndices[i][0] < columnCount-lbpBlockRadius && 
				seedIndices[i][1] >= lbpBlockRadius && seedIndices[i][1] <  rowCount-lbpBlockRadius){
		
				int[] coordinates = {seedIndices[i][0],seedIndices[i][1]};
				lbpHist = lbp.histc(lbp.reshape(lbp2D,coordinates[0]-lbpBlockRadius,coordinates[0]+lbpBlockRadius,coordinates[1]-lbpBlockRadius,coordinates[1]+lbpBlockRadius));
				pixelQueue.add(new NextPixel(1.0-lbp.checkClose(lbpHist,lbpModelHist),coordinates));
			}
			
			
		}
		/*Grow Region*/
		NextPixel nextPixel;
		int[][] neighbourhood = new int[4][2];
		int[] coordinates;
		while (pixelQueue.size() > 0){ //Go through all cells in queue
			nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			/*In case the pixel has been visited subsequent to having been added*/
			while (visited[nextPixel.coordinates[0]][nextPixel.coordinates[1]] == 1 && pixelQueue.size() > 0){
				nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			}
			/*	Add 4-connected neighbourhood to the  queue, unless the
			neighbourhood pixels have already been visited or are part of the
			mask already		*/
			if (nextPixel.cost <= maxDiff){    //If cost is still less than maxDiff
				coordinates = nextPixel.coordinates;
				//System.out.println("r "+coordinates[0]+" c "+coordinates[1]);
				visited[coordinates[0]][coordinates[1]] = (byte) 1;
				if (segmentationMask[coordinates[0]][coordinates[1]] ==0){
					segmentationMask[coordinates[0]][coordinates[1]] = 1;
				}
				
				
				
				//Check 4-connected neighbour
				neighbourhood[0][0] = coordinates[0]-1;	/*Left one*/
				neighbourhood[1][0] = coordinates[0]+1;	/*Right one*/
				neighbourhood[2][0] = coordinates[0];
				neighbourhood[3][0] = coordinates[0];
				
				neighbourhood[0][1] = coordinates[1];
				neighbourhood[1][1] = coordinates[1];
				neighbourhood[2][1] = coordinates[1]-1;	/*Up one*/
				neighbourhood[3][1] = coordinates[1]+1;	/*Down one*/

				//System.out.println("Qlength "+pixelQueue.size()+" mean "+currentMean+" alt Mean "+currentMean2+" area "+maskArea);
				checkNeighboursLBP(neighbourhood);
			}else{ //First pixel with higher than maxDiff cost or run out of pixels
				//System.out.println("Break");
				break;
			}
        
		}
		return true;
	}
	
	/*Update pixel queue*/
	protected void checkNeighboursLBP(int[][] neighbourhood){
		int[] coordinates;
		double[] lbpHist;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= lbpBlockRadius && coordinates[0] < columnCount-lbpBlockRadius && 
				coordinates[1] >=lbpBlockRadius && coordinates[1] <  rowCount-lbpBlockRadius){ //If the neigbour is within the image...
               if (visited[coordinates[0]][coordinates[1]] == (byte) 0 && segmentationMask[coordinates[0]][coordinates[1]] == 0){
					int[] queueCoordinates = {coordinates[0],coordinates[1]};
					lbpHist = lbp.histc(lbp.reshape(lbp2D,coordinates[0]-lbpBlockRadius,coordinates[0]+lbpBlockRadius,coordinates[1]-lbpBlockRadius,coordinates[1]+lbpBlockRadius));
					pixelQueue.add(new NextPixel(1.0-lbp.checkClose(lbpHist,lbpModelHist),queueCoordinates));
               }
            }
        }
	}
	
	
	
	public boolean growRegion(){
		/*Init variables and add seed points to the queue*/
		rowCount = dataSlice[0].length;
		columnCount = dataSlice.length;
		pixelQueue = new PriorityQueue<NextPixel>();	/*Try to reserve memory to enable faster execution...*/
		
		visited = new byte[columnCount][rowCount];

		/*Init pixelQueue*/
		int[][] seedIndices = find(segmentationMask);
		if (seedIndices == null){return false;}
		for (int i = 0; i<seedIndices.length; ++i){
			int[] coordinates = {seedIndices[i][0],seedIndices[i][1]};
			pixelQueue.add(new NextPixel(Math.abs(dataSlice[seedIndices[i][0]][seedIndices[i][1]]-currentMean),coordinates));
		}
		
		/*Grow Region*/
		NextPixel nextPixel;
		int[][] neighbourhood = new int[4][2];
		int[] coordinates;
		while (pixelQueue.size() > 0){ //Go through all cells in queue
			nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			/*In case the pixel has been visited subsequent to having been added*/
			while (visited[nextPixel.coordinates[0]][nextPixel.coordinates[1]] == 1 && pixelQueue.size() > 0){
				nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			}
			/*	Add 4-connected neighbourhood to the  queue, unless the
			neighbourhood pixels have already been visited or are part of the
			mask already		*/
			if (nextPixel.cost <= maxDiff){    //If cost is still less than maxDiff
				coordinates = nextPixel.coordinates;
				//System.out.println("r "+coordinates[0]+" c "+coordinates[1]);
				visited[coordinates[0]][coordinates[1]] = (byte) 1;
				if (segmentationMask[coordinates[0]][coordinates[1]] ==0){
					segmentationMask[coordinates[0]][coordinates[1]] = 1;
					++maskArea;	//Add the new pixel to the area
					currentMean += ((dataSlice[coordinates[0]][coordinates[1]]-currentMean)/((double) maskArea)); //Adding the weighted difference updates the mean...
					//currentMean = getCurrentMean();  //The mean may be updated to include the new pixel. Might work just as well without update with several seeds...
				}
				
				
				
				//Check 4-connected neighbour
				neighbourhood[0][0] = coordinates[0]-1;	/*Left one*/
				neighbourhood[1][0] = coordinates[0]+1;	/*Right one*/
				neighbourhood[2][0] = coordinates[0];
				neighbourhood[3][0] = coordinates[0];
				
				neighbourhood[0][1] = coordinates[1];
				neighbourhood[1][1] = coordinates[1];
				neighbourhood[2][1] = coordinates[1]-1;	/*Up one*/
				neighbourhood[3][1] = coordinates[1]+1;	/*Down one*/

				//System.out.println("Qlength "+pixelQueue.size()+" mean "+currentMean+" alt Mean "+currentMean2+" area "+maskArea);
				checkNeighbours(neighbourhood);
			}else{ //First pixel with higher than maxDiff cost or run out of pixels
				//System.out.println("Break");
				break;
			}
        
		}
		return true;
	}
	
	/*Update pixel queue*/
	protected void checkNeighbours(int[][] neighbourhood){
		int[] coordinates;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < columnCount && coordinates[1] >=0 && coordinates[1] < rowCount){ //If the neigbour is within the image...
               if (visited[coordinates[0]][coordinates[1]] == (byte) 0 && segmentationMask[coordinates[0]][coordinates[1]] == 0){
					int[] queueCoordinates = {coordinates[0],coordinates[1]};
                  pixelQueue.add(new NextPixel(Math.abs(dataSlice[coordinates[0]][coordinates[1]]-currentMean),queueCoordinates));
               }
            }
        }
	}
	
	protected int[][] find(byte[][] matrix){
		int[][] temp = new int[matrix.length*matrix[0].length][2];
		int found = 0;
		for (int i = 0; i< matrix.length;++i){
			for (int j = 0; j< matrix[i].length;++j){
				if (matrix[i][j] > 0){
					temp[found][0] = i;
					temp[found][1] = j;
					++found;					
				}
			}
		}
		int[][] indices = new int[found][2];
		for (int i = 0; i<found; ++i){
			for (int j = 0; j< 2; ++j){
				indices[i][j] = temp[i][j];
			}
		}
		return indices;
	}
	
	protected double getCurrentMean(){
		int[][] indices = find(segmentationMask);
		double sum = 0;
		for (int i = 0; i<indices.length; ++i){
			sum+= dataSlice[indices[i][0]][indices[i][1]];
		}
		sum/=((double) indices.length);
		return sum;
	}
	
	protected double getStdev(double mean){
		int[][] indices = find(segmentationMask);
		double stDev = 0;
		for (int i = 0; i<indices.length; ++i){
			stDev+= Math.pow(dataSlice[indices[i][0]][indices[i][1]]-mean,2.0);
		}
		stDev/=((double) indices.length);
		return Math.sqrt(stDev);
	}
	
		
	/*Erode, fill holes and dilate functions for removing extra stuff*/
	public void erodeMask(){
		int rowCount =  segmentationMask[0].length;
		int columnCount = segmentationMask.length;
		for (int i=0; i<columnCount; i++){
			for (int j=0; j<rowCount; j++){
				if (segmentationMask[i][j] == 1){
					if (i>0 && segmentationMask[i-1][j]==0 ||
						j>0 && segmentationMask[i][j-1]==0 ||
						i+1<columnCount && segmentationMask[i+1][j]==0 ||
						j+1<rowCount && segmentationMask[i][j+1]==0)
						{segmentationMask[i][j] = -1;}	//Erode the pixel if any of the neighborhood pixels is background
				}
			}
		}
		
		for (int i=0; i<columnCount; i++){
			for (int j=0; j<rowCount; j++){
				if (segmentationMask[i][j]==-1){
					segmentationMask[i][j] = 0;
				}
			}
		}
	}
	
	/*
	Fill voids within mask...
	First fill the surroundings
	then fill the remainder, which should be the holes...
	
	*/
	public void fillVoids(){
		int rowCount =  segmentationMask[0].length;
		int columnCount = segmentationMask.length;
		byte[][] background = new byte[columnCount][rowCount];
		Vector<int[]> queue = new Vector<int[]>(rowCount*columnCount*4);
		int[][] borderPixels = new int[(columnCount-1+rowCount-1)*2][2];
		int bPi = 0;
		/*Add top and bottom border*/
		for (int i = 0; i<columnCount;++i){
			/*Top*/
			borderPixels[bPi][0] = i;
			borderPixels[bPi][1] = 0;
			bPi++;
			/*Bottom*/
			borderPixels[bPi][0] = i;
			borderPixels[bPi][1] = rowCount-1;
			bPi++;
		}
		/*Add left and right borders. N.B. corners have already been added...*/
		for (int j = 1; j<rowCount-1;++j){
			/*Top*/
			borderPixels[bPi][0] = 0;
			borderPixels[bPi][1] = j;
			bPi++;
			/*Bottom*/
			borderPixels[bPi][0] = columnCount-1;
			borderPixels[bPi][1] = j;
			bPi++;
		}
		int[] coordinates = new int[2];
		for (int bp = 0;bp<borderPixels.length;++bp){
			/*Start the filling from each border pixel*/
			coordinates[0] = borderPixels[bp][0];
			coordinates[1] = borderPixels[bp][1];
			queue.add(coordinates);	/*Start filling from 0,0...*/
			/*Fill background*/
			int[][] neighbourhood = new int[4][2];
			while (queue.size()>0){
				coordinates=queue.lastElement();
				queue.remove(queue.size()-1);
				if (background[coordinates[0]][coordinates[1]] == 0 && segmentationMask[coordinates[0]][coordinates[1]] < 1){
					background[coordinates[0]][coordinates[1]] = 1;
					
					//Check 4-connected neighbour
					neighbourhood[0][0] = coordinates[0]-1;	/*Left one*/
					neighbourhood[1][0] = coordinates[0]+1;	/*Right one*/
					neighbourhood[2][0] = coordinates[0];
					neighbourhood[3][0] = coordinates[0];
					
					neighbourhood[0][1] = coordinates[1];
					neighbourhood[1][1] = coordinates[1];
					neighbourhood[2][1] = coordinates[1]-1;	/*Up one*/
					neighbourhood[3][1] = coordinates[1]+1;	/*Down one*/
					//check whether the neighbour to the left should be added to the queue
					Vector<Object> returned = checkNeighbours(neighbourhood,segmentationMask, background,queue);
					segmentationMask = (byte[][])returned.get(0);
					background = (byte[][])returned.get(1);
					queue = (Vector<int[]>)returned.get(2);
				}			
			}
		}
		
		/*Background filled*/
		
		for (int i=0; i<columnCount; i++){
			for (int j=0; j<rowCount; j++){
				if (segmentationMask[i][j]<0.5 && background[i][j] == 0){
					segmentationMask[i][j] = 1;
				}
			}
		}
	}	

	/*Update pixel queue*/
	protected Vector<Object> checkNeighbours(int[][] neighbourhood,byte[][] segmentationMask, byte[][] background,Vector<int[]> queue){
		int[] coordinates;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < columnCount && coordinates[1] >=0 && coordinates[1] < rowCount
				&& segmentationMask[coordinates[0]][coordinates[1]] == 0 && background[coordinates[0]][coordinates[1]]==0){ //If the neigbour is within the image...
					int[] queueCoordinates = {coordinates[0],coordinates[1]};
					queue.add(queueCoordinates);
            }
        }
		Vector<Object> returnValue = new Vector<Object>();
		returnValue.add(segmentationMask);
		returnValue.add(background);
		returnValue.add(queue);
		return returnValue;
	}
	
	/*Test*/
	public static void main(String[] are){
		double[][] image = {
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,500,500,500,251,249,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}
						};
		byte[][] mask = {
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,1,1,1,0,0,0,0},
						{0,0,0,1,1,1,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}
						};
		RegionGrow2D rg = new RegionGrow2D(image, mask);
		rg.growRegion();
		rg.printGrown();
		
	}
	public void printGrown(){
		for (int c = 0; c<segmentationMask.length;++c){
			for (int r = 0; r<segmentationMask[c].length;++r){
				System.out.print((int) segmentationMask[c][r]+"\t");
			}
			System.out.print("\n");
		}
	}
}








