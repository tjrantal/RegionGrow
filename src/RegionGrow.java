/*
	Created by Timo Rantalainen
*/

package ijGrower;

import java.util.PriorityQueue;
import java.util.Vector;

public abstract class RegionGrow{
	
	
	/*Parameters*/

	public double maxDiff;
	public boolean success;
	
	/*Global variables, saves effort in declaring functions...*/
	public int rowCount;
	public int columnCount;
	public double currentMean;
	public long maskArea;
	public PriorityQueue<NextPixel> pixelQueue;
	
	private boolean maskHasPixels(){
		/*Implement in subclasses*/
		return true;
	}
	
	private boolean growRegion(){
		/*Implement in subclasses*/
		return true;
	}
	
	private double getCurrentMean(){
		/*Implement in subclasses*/
		return 0;
	}
	
	/*Get 3D Stack mask mean*/
	public static double[] getCurrentMeanAndArea(byte[][][] segmentationMask, double[][][] dataSlice){
		int[][] indices = findStatic(segmentationMask);
		double sum = 0;
		for (int i = 0; i<indices.length; ++i){
			sum+= dataSlice[indices[i][0]][indices[i][1]][indices[i][2]];
		}
		sum/=((double) indices.length);
		double[] returnValue = {sum, (double) indices.length};
		return returnValue;
	}
	
	/*Find mask indices from 3D stack*/
	public static int[][] findStatic(byte[][][] matrix){
		int[][] temp = new int[matrix.length*matrix[0].length*matrix[0][0].length][3];
		int found = 0;
		for (int i = 0; i< matrix.length;++i){
			for (int j = 0; j< matrix[i].length;++j){
				for (int k = 0; k< matrix[i][j].length;++k){
					if (matrix[i][j][k] > 0){
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


	
}








