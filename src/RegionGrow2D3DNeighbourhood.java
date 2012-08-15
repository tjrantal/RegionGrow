/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;
import java.util.Vector;

public class RegionGrow2D3DNeighbourhood extends RegionGrow{
	
	
	/*Parameters*/
	protected double[][][] dataSlice;
	protected double[][][] gradientSlice;
	public byte[][][] segmentationMask;
	protected int slice;
	protected int depth;
	protected double greyDiff;
	protected double gradientDiff;
	protected double gradientMean;
	
	/*Global variables, saves effort in declaring functions...*/
	protected byte[][] visited;
	
	/*Constructor with maxDiff, mean and area*/
	public RegionGrow2D3DNeighbourhood(double[][][] dataSlice, double[][][] gradientSlice, byte[][][] segmentationMask, int slice, double maxDiff,double greyDiff, double currentMean, double gradientDiff, double gradientMean, long maskArea){
		this.dataSlice = dataSlice;
		this.gradientSlice = gradientSlice;
		this.segmentationMask = segmentationMask;
		this.slice = slice;
		this.maxDiff = maxDiff;
		this.greyDiff = greyDiff;
		this.currentMean = currentMean;
		this.gradientDiff = gradientDiff;
		this.gradientMean = gradientMean;
		this.maskArea = maskArea;
	}
	
	
	public boolean growRegion(){
		/*Init variables and add seed points to the queue*/
		rowCount = dataSlice[0].length;
		columnCount = dataSlice.length;
		depth =dataSlice[0][0].length;
		pixelQueue = new PriorityQueue<NextPixel>();	/*Try to reserve memory to enable faster execution...*/
		
		visited = new byte[columnCount][rowCount];

		/*Init pixelQueue*/
		int[][] seedIndices = find(segmentationMask,slice);
		if (seedIndices == null){return false;}
		double cost;
		double gradientCost;
		int[][] costNeighbourhood = new int[9][3];
						//Check coronal plane neighbours
				costNeighbourhood[0][0] = -1;	/*Left one*/
				costNeighbourhood[1][0] = 0;
				costNeighbourhood[2][0] = +1;	/*Right one*/
				costNeighbourhood[3][0] = -1;	/*Left one*/
				costNeighbourhood[4][0] = 0;
				costNeighbourhood[5][0] = +1;	/*Right one*/
				costNeighbourhood[6][0] = -1;	/*Left one*/
				costNeighbourhood[7][0] = 0;
				costNeighbourhood[8][0] = +1;	/*Right one*/
				
				costNeighbourhood[0][1] = 0;
				costNeighbourhood[1][1] = 0;
				costNeighbourhood[2][1] = 0;
				costNeighbourhood[3][1] = 0;
				costNeighbourhood[4][1] = 0;
				costNeighbourhood[5][1] = 0;
				costNeighbourhood[6][1] = 0;
				costNeighbourhood[7][1] = 0;
				costNeighbourhood[8][1] = 0;


				costNeighbourhood[0][2] = -1;	/*Closer one*/	
				costNeighbourhood[1][2] = -1;	/*Closer one*/	
				costNeighbourhood[2][2] = -1;	/*Closer one*/	
				costNeighbourhood[3][2] = 0;
				costNeighbourhood[4][2] = 0;	
				costNeighbourhood[5][2] = 0;	
				costNeighbourhood[6][2] = +1;	/*Further one*/
				costNeighbourhood[7][2] = +1;	/*Further one*/
				costNeighbourhood[8][2] = +1;	/*Further one*/
		
		for (int i = 0; i<seedIndices.length; ++i){
			int[] coordinates = {seedIndices[i][0],seedIndices[i][1]};
			cost =Math.abs(dataSlice[seedIndices[i][0]][seedIndices[i][1]][slice]-currentMean)/greyDiff;
			//cost +=Math.abs(gradientSlice[seedIndices[i][0]][seedIndices[i][1]]-gradientMean)/gradientDiff;
			gradientCost = Math.abs(gradientSlice[seedIndices[i][0]][seedIndices[i][1]][slice]-gradientMean)/gradientDiff;
			pixelQueue.add(new NextPixel(cost,gradientCost,coordinates));
		}
		
		/*Grow Region*/
		NextPixel nextPixel;
		int[][] neighbourhood = new int[4][2];
				neighbourhood[0][0] =-1;	/*Left one*/
				neighbourhood[1][0] =+1;	/*Right one*/
				neighbourhood[2][0] = 0;
				neighbourhood[3][0] = 0;
				
				neighbourhood[0][1] = 0;
				neighbourhood[1][1] = 0;
				neighbourhood[2][1] = -1;	/*Up one*/
				neighbourhood[3][1] = +1;	/*Down one*/

		int[] coordinates;
		int[] coordinateToCheck = new int[2];
		int horizontalNeighbourhood;
		while (pixelQueue.size() > 0){ //Go through all cells in queue
			nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			/*In case the pixel has been visited subsequent to having been added*/
			while ((visited[nextPixel.coordinates[0]][nextPixel.coordinates[1]] == 1 || nextPixel.gradientCost>1.0) && pixelQueue.size() > 0){
				nextPixel  = pixelQueue.poll();	/*Get the pixel with the lowest cost and remove it from the queue*/
			}
			/*	Add 4-connected neighbourhood to the  queue, unless the
			neighbourhood pixels have already been visited or are part of the
			mask already		*/
			//System.out.println("Bef cost "+nextPixel.cost+" gCost "+nextPixel.gradientCost);
			if (nextPixel.gradientCost<=1.0 && nextPixel.cost <= 1.0){    //If cost is still less than maxDiff
				//System.out.println("cost "+nextPixel.cost+" gCost "+nextPixel.gradientCost);
				coordinates = nextPixel.coordinates;
				//System.out.println("r "+coordinates[0]+" c "+coordinates[1]);
				visited[coordinates[0]][coordinates[1]] = (byte) 1;
				if (segmentationMask[coordinates[0]][coordinates[1]][slice] ==0){
					segmentationMask[coordinates[0]][coordinates[1]][slice] = 1;
					++maskArea;	//Add the new pixel to the area
					currentMean += ((dataSlice[coordinates[0]][coordinates[1]][slice]-currentMean)/((double) maskArea)); //Adding the weighted difference updates the mean...
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
			
				

				for (int r = 0;r<neighbourhood.length;++r){
					coordinateToCheck[0] =  coordinates[0]+neighbourhood[r][0];
					coordinateToCheck[1] =  coordinates[1]+neighbourhood[r][1];
					if (coordinateToCheck[0] >= 0 && coordinateToCheck[0] < columnCount && coordinateToCheck[1] >=0 && coordinateToCheck[1] < rowCount){ //If the neigbour is within the image...
					   if (visited[coordinateToCheck[0]][coordinateToCheck[1]] == (byte) 0 && segmentationMask[coordinateToCheck[0]][coordinateToCheck[1]][slice] == 0){
							/*Check the horizonal plane*/
							horizontalNeighbourhood = 0;
							for (int h = 0; h< costNeighbourhood.length;++h){
								if (coordinateToCheck[0]+costNeighbourhood[h][0] >= 0 && coordinateToCheck[0]+costNeighbourhood[h][0] < columnCount && coordinateToCheck[1]+costNeighbourhood[h][1] >=0 && coordinateToCheck[1]+costNeighbourhood[h][1] < rowCount && slice+costNeighbourhood[h][2] >=0 && slice+costNeighbourhood[h][2] < depth){ //If the neigbour is within the image...
									if(Math.abs(dataSlice[coordinateToCheck[0]+costNeighbourhood[h][0]][coordinateToCheck[1]+costNeighbourhood[h][1]][slice+costNeighbourhood[h][2]]-currentMean)/greyDiff <= 1.0){
										++horizontalNeighbourhood;
										if (horizontalNeighbourhood > 5){break;}
									}
								}
							}
							if (horizontalNeighbourhood > 5){					   
								int[] queueCoordinates = {coordinateToCheck[0],coordinateToCheck[1]};
								cost =Math.abs(dataSlice[coordinateToCheck[0]][coordinateToCheck[1]][slice]-currentMean)/greyDiff;
								//cost +=Math.abs(gradientSlice[coordinates[0]][coordinates[1]]-gradientMean)/gradientDiff;
								gradientCost = Math.abs(gradientSlice[coordinateToCheck[0]][coordinateToCheck[1]][slice]-gradientMean)/gradientDiff;
								pixelQueue.add(new NextPixel(cost,gradientCost,queueCoordinates));
							}
					   }
					}
				}
				
				
			}else{ //First pixel with higher than maxDiff cost or run out of pixels
				//System.out.println("Break");
				break;
			}
        
		}
		return true;
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
	
	protected int[][] find(byte[][][] matrix,int slice){
		int[][] temp = new int[matrix.length*matrix[0].length][2];
		int found = 0;
		for (int i = 0; i< matrix.length;++i){
			for (int j = 0; j< matrix[i].length;++j){
				if (matrix[i][j][slice] > 0){
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
	
	
		
	/*Erode, fill holes and dilate functions for removing extra stuff*/
	public void erodeMask(){
		int rowCount =  segmentationMask[0].length;
		int columnCount = segmentationMask.length;
		for (int i=0; i<columnCount; i++){
			for (int j=0; j<rowCount; j++){
				if (segmentationMask[i][j][slice] == 1){
					if (i>0 && segmentationMask[i-1][j][slice]==0 ||
						j>0 && segmentationMask[i][j-1][slice]==0 ||
						i+1<columnCount && segmentationMask[i+1][j][slice]==0 ||
						j+1<rowCount && segmentationMask[i][j+1][slice]==0)
						{segmentationMask[i][j][slice] = -1;}	//Erode the pixel if any of the neighborhood pixels is background
				}
			}
		}
		
		for (int i=0; i<columnCount; i++){
			for (int j=0; j<rowCount; j++){
				if (segmentationMask[i][j][slice]==-1){
					segmentationMask[i][j][slice] = 0;
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
				if (background[coordinates[0]][coordinates[1]] == 0 && segmentationMask[coordinates[0]][coordinates[1]][slice] < 1){
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
					segmentationMask = (byte[][][])returned.get(0);
					background = (byte[][])returned.get(1);
					queue = (Vector<int[]>)returned.get(2);
				}			
			}
		}
		
		/*Background filled*/
		
		for (int i=0; i<columnCount; i++){
			for (int j=0; j<rowCount; j++){
				if (segmentationMask[i][j][slice]<0.5 && background[i][j] == 0){
					segmentationMask[i][j][slice] = 1;
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
	
		/*Update pixel queue*/
	protected Vector<Object> checkNeighbours(int[][] neighbourhood,byte[][][] segmentationMask, byte[][] background,Vector<int[]> queue){
		int[] coordinates;
        for (int r = 0;r<neighbourhood.length;++r){
			coordinates = neighbourhood[r];
            if (coordinates[0] >= 0 && coordinates[0] < columnCount && coordinates[1] >=0 && coordinates[1] < rowCount
				&& segmentationMask[coordinates[0]][coordinates[1]][slice] == 0 && background[coordinates[0]][coordinates[1]]==0){ //If the neigbour is within the image...
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

}








