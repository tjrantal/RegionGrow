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
	
	/*LBP parameters*/
	protected int lbpBlockRadius;
	protected double[] lbpModelHist;
	protected LBP lbp;
	
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
	
	/*Set LBP model histogram*/
	public void setLBPModel(double[] modelHist){
		lbpModelHist = new double[modelHist.length];
		for (int i = 0; i<modelHist.length;++i){
			lbpModelHist[i] = modelHist[i];
		}
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
	
	public static double getStdev(byte[][][] segmentationMask, double[][][] dataSlice,double mean){
		int[][] indices = findStatic(segmentationMask);
		double stDev = 0;
		for (int i = 0; i<indices.length; ++i){
			stDev+= Math.pow(dataSlice[indices[i][0]][indices[i][1]][indices[i][2]]-mean,2.0);
		}
		stDev/=((double) indices.length);
		return Math.sqrt(stDev);
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

	public static byte[][][] dilateSlices(byte[][][] mask, double[][][] data, double[] dilateLimits){
		int width = mask.length;
		int height = mask[0].length;
		int depth = mask[0][0].length;
		byte dilateVal = 1;
		byte min = 0;
		byte temp = -1;
		int[][] neighbours = {{-1,0},{1,0},{0,-1},{0,1}};
		for (int d = 0; d<depth;++d){
			for (int i=0; i<width; ++i){
				for (int j=0; j<height; ++j){
					if (mask[i][j][d] ==dilateVal){
						for (int n = 0; n<neighbours.length;++n){
							if (i>0 && i <width-1 && j > 0 && j<height-1 && 
								mask[i+neighbours[n][0]][j+neighbours[n][1]][d]==min && 
								data[i+neighbours[n][0]][j+neighbours[n][1]][d]	>= dilateLimits[0] && 
								data[i+neighbours[n][0]][j+neighbours[n][1]][d]	<= dilateLimits[1]){
									mask[i+neighbours[n][0]][j+neighbours[n][1]][d] = temp;
							}
						}
					}
				}
			}
		}
		
		for (int d = 0; d<depth;++d){
			for (int i=0; i<width; ++i){
				for (int j=0; j<height; ++j){
					if (mask[i][j][d] == temp){
						mask[i][j][d] = dilateVal;	//Set to proper value here...
					}
				}
			}
		}
		return mask;
	}
	
	/*Get 3D Stack max and maxIndice*/
	public static Max getMax(double[][][] dataSlice){
		double max = Double.NEGATIVE_INFINITY;
		int[] indices = new int[3];
		for (int i = 0; i<dataSlice.length; ++i){
			for (int j = 0; j<dataSlice[i].length; ++j){
				for (int k = 0; k<dataSlice[i][j].length; ++k){
					if (dataSlice[i][j][k] > max){
						max = dataSlice[i][j][k];
						indices[0] = i;
						indices[1] = j;
						indices[2] = k;
					}
				}
			}
		}
		Max returnValue = new Max(max,indices);
		return returnValue;
	}
		/*Get 2D Stack max and maxIndice*/
	public static Max getMax(double[][] dataSlice){
		double max = Double.NEGATIVE_INFINITY;
		int[] indices = new int[2];
		for (int i = 0; i<dataSlice.length; ++i){
			for (int j = 0; j<dataSlice[i].length; ++j){
					if (dataSlice[i][j] > max){
						max = dataSlice[i][j];
						indices[0] = i;
						indices[1] = j;
					}
			}
		}
		Max returnValue = new Max(max,indices);
		return returnValue;
	}
}








