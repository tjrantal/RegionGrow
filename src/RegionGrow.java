/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;

public class RegionGrow{
	
	
	/*Parameters*/
	private double[][] dataSlice;
	public double[][] segmentationMask;
	private double maxDiff;
	
	/*Global variables, saves effort in declaring functions...*/
	private int rowCount;
	private int columnCount;
	private byte[][] visited;
	private double currentMean;
	private PriorityQueue<NextPixel> pixelQueue;
	/*Constructor for default maxDiff*/
	public RegionGrow(double[][] dataSlice, double[][] segmentationMask){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = 250.0;
		System.out.println("Constructor w/o");
		growRegion();
	}
	
	/*Constructor with maxDiff*/
	public RegionGrow(double[][] dataSlice, double[][] segmentationMask, double maxDiff){
		this.dataSlice = dataSlice;
		this.segmentationMask = segmentationMask;
		this.maxDiff = maxDiff;
		System.out.println("Constructor w");
		growRegion();
	}
	
	private void growRegion(){
		/*Init variables and add seed points to the queue*/
		pixelQueue = new PriorityQueue<NextPixel>();
		rowCount = dataSlice.length;
		columnCount = dataSlice[0].length;
		visited = new byte[rowCount][columnCount];
		
		currentMean = getCurrentMean();
			
		System.out.println("Start Init");
		/*Init pixelQueue*/
		int[][] seedIndices = find(segmentationMask);

		for (int i = 0; i<seedIndices.length; ++i){
			int[] coordinates = {seedIndices[i][0],seedIndices[i][1]};
			pixelQueue.add(new NextPixel(Math.abs(dataSlice[seedIndices[i][0]][seedIndices[i][1]]-currentMean),coordinates));
		}
		
		/*Grow Region*/
		NextPixel nextPixel;
		System.out.println("Start Growing");
		long maskArea = seedIndices.length;
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
				if (segmentationMask[coordinates[0]][coordinates[1]] < 1){
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
	
	private int[][] find(double[][] matrix){
		int[][] temp = new int[matrix.length*matrix[0].length][2];
		int found = 0;
		for (int i = 0; i< matrix.length;++i){
			for (int j = 0; j< matrix[i].length;++j){
				if (matrix[i][j] > 0.5){
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
		double[][] mask = {
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








