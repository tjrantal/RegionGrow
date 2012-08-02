/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;
import java.util.Vector;

public class RegionGrow2DVariance extends RegionGrow2D{
	
	
	/*Parameters*/
	private double[][] varianceSlice;
	
	/*Global variables, saves effort in declaring functions...*/
	private double varianceMean;

	/*Constructor for default maxDiff*/
	public RegionGrow2DVariance(double[][] dataSlice, byte[][] segmentationMask, double[][] varianceSlice){
		super(dataSlice,segmentationMask);
	}
	
	/*Constructor with maxDiff*/
	public RegionGrow2DVariance(double[][] dataSlice, byte[][] segmentationMask, double[][] varianceSlice, double maxDiff){
		super(dataSlice,segmentationMask,maxDiff);
	}
	
	/*Constructor with maxDiff, mean and area*/
	public RegionGrow2DVariance(double[][] dataSlice, byte[][] segmentationMask, double[][] varianceSlice, double maxDiff, double currentMean, long maskArea, double varianceMean){
		super(dataSlice,segmentationMask,maxDiff,currentMean,maskArea);
		this.varianceSlice = varianceSlice;
		this.varianceMean = varianceMean;
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
		rowCount = dataSlice[0].length;
		columnCount = dataSlice.length;
		pixelQueue = new PriorityQueue<NextPixel>();	/*Try to reserve memory to enable faster execution...*/
		
		visited = new byte[columnCount][rowCount];

		/*Init pixelQueue*/
		int[][] seedIndices = super.find(segmentationMask);
		if (seedIndices == null){return false;}
		double cost;
		for (int i = 0; i<seedIndices.length; ++i){
			int[] coordinates = {seedIndices[i][0],seedIndices[i][1]};
			cost = Math.abs(dataSlice[seedIndices[i][0]][seedIndices[i][1]]-currentMean)+Math.abs(varianceSlice[seedIndices[i][0]][seedIndices[i][1]]-varianceMean);
			pixelQueue.add(new NextPixel(cost,coordinates));
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
				System.out.println("Break");
				break;
			}
        
		}
		return true;
	}
	
	/*Update pixel queue*/
	protected void checkNeighbours(int[][] neighbourhood){
		int[] coordinates;
		double cost;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < columnCount && coordinates[1] >=0 && coordinates[1] < rowCount){ //If the neigbour is within the image...
               if (visited[coordinates[0]][coordinates[1]] == (byte) 0 && segmentationMask[coordinates[0]][coordinates[1]] == 0){
					int[] queueCoordinates = {coordinates[0],coordinates[1]};
					cost = Math.abs(dataSlice[coordinates[0]][coordinates[1]]-currentMean)+2.0*Math.abs(varianceSlice[coordinates[0]][coordinates[1]]-varianceMean);
                  pixelQueue.add(new NextPixel(cost,queueCoordinates));
               }
            }
        }
	}
}








