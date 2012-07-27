/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;
import java.util.Vector;

public class RegionGrow{
	
	
	/*Parameters*/
	private double[][] dataSlice;
	public byte[][] segmentationMask;
	private double maxDiff;
	public boolean success;
	
	/*Global variables, saves effort in declaring functions...*/
	private int rowCount;
	private int columnCount;
	private byte[][] visited;
	private double currentMean;
	private long maskArea;
	private PriorityQueue<NextPixel> pixelQueue;
	/*Constructor for default maxDiff*/
	public RegionGrow(double[][] dataSlice, byte[][] segmentationMask){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = 250.0;
		currentMean = getCurrentMean();
	}
	
	/*Constructor with maxDiff*/
	public RegionGrow(double[][] dataSlice, byte[][] segmentationMask, double maxDiff){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		currentMean = getCurrentMean();
	}
	
	/*Constructor with maxDiff, mean and area*/
	public RegionGrow(double[][] dataSlice, byte[][] segmentationMask, double maxDiff, double currentMean, long maskArea){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		this.currentMean = currentMean;
		this.maskArea = maskArea;
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
	
	public boolean growRegion(){
		/*Init variables and add seed points to the queue*/
		rowCount = dataSlice.length;
		columnCount = dataSlice[0].length;
		pixelQueue = new PriorityQueue<NextPixel>();	/*Try to reserve memory to enable faster execution...*/
		
		visited = new byte[rowCount][columnCount];

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
				neighbourhood[0][0] = coordinates[0]-1;	/*Up one*/
				neighbourhood[1][0] = coordinates[0]+1;	/*Down one*/
				neighbourhood[2][0] = coordinates[0];
				neighbourhood[3][0] = coordinates[0];
				
				neighbourhood[0][1] = coordinates[1];
				neighbourhood[1][1] = coordinates[1];
				neighbourhood[2][1] = coordinates[1]-1;	/*Left one*/
				neighbourhood[3][1] = coordinates[1]+1;	/*Right one*/

				//System.out.println("Qlength "+pixelQueue.size()+" mean "+currentMean+" alt Mean "+currentMean2+" area "+maskArea);
				checkNeighbours(neighbourhood);
			}else{ //First pixel with higher than maxDiff cost or run out of pixels
				System.out.println("Break");
				break;
			}
        
		}
		return true;
	}
	
	/*Update pixel queue*/
	private void checkNeighbours(int[][] neighbourhood){
		int[] coordinates;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < rowCount && coordinates[1] >=0 && coordinates[1] < columnCount){ //If the neigbour is within the image...
               if (visited[coordinates[0]][coordinates[1]] == (byte) 0 && segmentationMask[coordinates[0]][coordinates[1]] == 0){
					int[] queCoordinates = {coordinates[0],coordinates[1]};
                  pixelQueue.add(new NextPixel(Math.abs(dataSlice[coordinates[0]][coordinates[1]]-currentMean),queCoordinates));
               }
            }
        }
	}
	
	private int[][] find(byte[][] matrix){
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
	
	private double getCurrentMean(){
		int[][] indices = find(segmentationMask);
		double sum = 0;
		for (int i = 0; i<indices.length; ++i){
			sum+= dataSlice[indices[i][0]][indices[i][1]];
		}
		sum/=((double) indices.length);
		return sum;
	}
	


   /*Next Pixel for pixel queue, comparable enables always getting the smallest value*/
	class NextPixel implements Comparable<NextPixel> {
		public int[] coordinates;
		public double cost;
		public NextPixel(double cost, int[] coordinates){
			this.cost =cost;
			this.coordinates = coordinates;
		}

		public int compareTo(NextPixel other){
			if( cost < other.cost){
				return -1;
			}else{ 
				if( cost > other.cost){ 
					return +1;
				}else{
					return 0;
				}
			}
		}

	}	    	

		
	/*Erode, fill holes and dilate functions for removing extra stuff*/
	public void erodeMask(){
		int rowCount =  segmentationMask.length;
		int columnCount = segmentationMask[0].length;
		for (int i=0; i<rowCount; i++){
			for (int j=0; j<columnCount; j++){
				if (segmentationMask[i][j] == 1){
					if (i>0 && segmentationMask[i-1][j]==0 ||
						j>0 && segmentationMask[i][j-1]==0 ||
						i+1<rowCount && segmentationMask[i+1][j]==0 ||
						j+1<columnCount && segmentationMask[i][j+1]==0)
						{segmentationMask[i][j] = -1;}	//Erode the pixel if any of the neighborhood pixels is background
				}
			}
		}
		
		for (int i=0; i<rowCount; i++){
			for (int j=0; j<columnCount; j++){
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
		int rowCount =  segmentationMask.length;
		int columnCount = segmentationMask[0].length;
		byte[][] background = new byte[rowCount][columnCount];
		Vector<int[]> queue = new Vector<int[]>(rowCount*columnCount*4);
		int[] coordinates = {0,0};
		queue.add(coordinates);	/*Start filling from 0,0...*/
		/*Fill background*/
		int[][] neighbourhood = new int[4][2];
		while (queue.size()>0){
			coordinates=queue.lastElement();
			queue.remove(queue.size()-1);
			if (background[coordinates[0]][coordinates[1]] == 0 && segmentationMask[coordinates[0]][coordinates[1]] < 1){
				background[coordinates[0]][coordinates[1]] = 1;
				
				//Check 4-connected neighbour
				neighbourhood[0][0] = coordinates[0]-1;	/*Up one*/
				neighbourhood[1][0] = coordinates[0]+1;	/*Down one*/
				neighbourhood[2][0] = coordinates[0];
				neighbourhood[3][0] = coordinates[0];
				
				neighbourhood[0][1] = coordinates[1];
				neighbourhood[1][1] = coordinates[1];
				neighbourhood[2][1] = coordinates[1]-1;	/*Left one*/
				neighbourhood[3][1] = coordinates[1]+1;	/*Right one*/
				//check whether the neighbour to the left should be added to the queue
				Vector<Object> returned = checkNeighbours(neighbourhood,segmentationMask, background,queue);
				segmentationMask = (byte[][])returned.get(0);
				background = (byte[][])returned.get(1);
				queue = (Vector<int[]>)returned.get(2);
			}			
		}
		/*Background filled*/
		
		for (int i=0; i<rowCount; i++){
			for (int j=0; j<columnCount; j++){
				if (segmentationMask[i][j]<0.5 && background[i][j] == 0){
					segmentationMask[i][j] = 1;
				}
			}
		}
	}	

	/*Update pixel queue*/
	private Vector<Object> checkNeighbours(int[][] neighbourhood,byte[][] segmentationMask, byte[][] background,Vector<int[]> queue){
		int[] coordinates;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < rowCount && coordinates[1] >=0 && coordinates[1] < columnCount
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
	
	/*
	public byte[] dilateMaskHonorColor(byte[] data,byte dilateVal,byte min, byte temp){
		//Dilate algorithm
		// Best dilate by one solution taken from http://ostermiller.org/dilate_and_erode.html
		for (int i=0; i<height; i++){
			for (int j=0; j<width; j++){
				if (data[i*width+j] ==dilateVal){
					if (i>0 && data[(i-1)*width+j]==min) {data[(i-1)*width+j] = temp;}
					if (j>0 && data[(i)*width+j-1]==min) {data[(i)*width+j-1] = temp;}
					if (i+1<height && data[(i+1)*width+j]==min) {data[(i+1)*width+j] = temp;}
					if (j+1<width && data[(i)*width+j+1]==min) {data[(i)*width+j+1] = temp;}
				}
			}
		}
		for (int i=0; i<width*height; i++){
			if (data[i] == temp){
				data[i] = dilateVal;	//Set to proper value here...
			}
		}
		return data;
	}
	*/
	
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
						{0,0,0,0,0,1,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0},
						{0,0,0,0,0,0,0,0,0,0}
						};
		RegionGrow rg = new RegionGrow(image, mask);
		rg.printGrown();
	}
	public void printGrown(){
		for (int r = 0; r<segmentationMask.length;++r){
			for (int c = 0; c<segmentationMask[r].length;++c){
				System.out.print((int) segmentationMask[r][c]+"\t");
			}
			System.out.print("\n");
		}
	}
}








