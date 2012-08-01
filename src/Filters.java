/*Bicubic interpolation copied from imageJ imageProcessor.*/

/*Some filtering functions reproduced here to enable using the code without ImageJ
	2D arrays, first pointer x (i.e. width), second pointer y (i.e. height): data[x][y]

*/

package	ijGrower;

import java.text.DecimalFormat;	/*For debugging*/

public class Filters{
	
	
	public static double[][] getVarianceImage(double[][] data, int radius){
		int width = data.length;
		int height = data[0].length;
		double[][] varianceImage = new double[width][height];
		double[] coordinates = new double[2];
		for (int i = 0+radius;i<width-(radius);++i){
			for (int j = 0+radius;j<width-(radius);++j){
				coordinates[0] = i;
				coordinates[1] = j;
				varianceImage[i][j] = getLocalVariance(data,coordinates,radius);
			}
		}
		return varianceImage;
	}
	
	/*Local variance with circular sampling. Eight samples per integer increment of radius*/
	public static double getLocalVariance(double[][] data,double[] coordinates,int radius){
		/*Init sampling coordinates*/
		double[][] samplingCoordinates = new double[8*radius+1][2];
		samplingCoordinates[8*radius] = coordinates;
		final double sqrt05 = Math.sqrt(0.5);
		final double[][] directions = {{1,0},{sqrt05,sqrt05},{0,1},{-sqrt05,sqrt05},{-1,0},{-sqrt05,-sqrt05},{0,-1},{sqrt05,-sqrt05}};
		for (int r=0;r<radius;++r){
			for (int t = 0;t <8; ++t){
				samplingCoordinates[t*r][0] = coordinates[0]+directions[t][0]*((double)(r+1));
				samplingCoordinates[t*r][1] = coordinates[1]+directions[t][1]*((double)(r+1));
			}
		}
		/*Get the values*/
		double[] values = new double[8*radius+1];
		for (int i = 0; i<samplingCoordinates.length;++i){
			values[i] = getBicubicInterpolatedPixel(samplingCoordinates[i][0],samplingCoordinates[i][1],data);
		}
		return getVariance(values);
	}
	
	public static double getMean(double[] data){
		double sum = 0;
		for (int i = 0; i<data.length; ++i){
			sum+= data[i];
		}
		sum/=((double) data.length);
		return sum;
	}
	
	public static double getVariance(double[] data){
		double variance = 0;
		double mean = getMean(data);
		for (int i = 0; i<data.length; ++i){
			variance+= Math.pow(data[i]-mean,2.0);
		}
		variance/=((double) data.length);
		return variance;
	}
	
	/** This method is from Chapter 16 of "Digital Image Processing:
		An Algorithmic Introduction Using Java" by Burger and Burge
		(http://www.imagingbook.com/). */
	public static double getBicubicInterpolatedPixel(double x0, double y0, double[][] data) {
		int u0 = (int) Math.floor(x0);	//use floor to handle negative coordinates too
		int v0 = (int) Math.floor(y0);
		int width = data.length;
		int height = data[0].length;
		if (u0<1 || u0>width-3 || v0< 1 || v0>height-3){
			if ((u0 == 0 || u0 < width-1) && (v0 == 0 || v0 < height-1)){ /*Use bilinear interpolation http://en.wikipedia.org/wiki/Bilinear_interpolation*/
				double x = (x0-(double)u0);
				double y = (y0-(double)v0);
				return data[u0][v0]*(1-x)*(1-y) 	/*f(0,0)(1-x)(1-y)*/
						+data[u0+1][v0]*(1-y)*x	/*f(1,0)x(1-y)*/
						+data[u0][v0+1]*(1-x)*y	/*f(0,1)(1-x)y*/
						+data[u0+1][v0+1]*x*y;	/*f(1,1)xy*/
			}
			return 0; /*Return zero for points outside the interpolable area*/
		}
		double q = 0;
		for (int j = 0; j < 4; ++j) {
			int v = v0 - 1 + j;
			double p = 0;
			for (int i = 0; i < 4; ++i) {
				int u = u0 - 1 + i;
				p = p + data[u][v] * cubic(x0 - u);
			}
			q = q + p * cubic(y0 - v);
		}
		return q;
	}
	
	
	public static final double cubic(double x) {
		final double a = 0.5; // Catmull-Rom interpolation
		if (x < 0.0) x = -x;
		double z = 0.0;
		if (x < 1.0) 
			z = x*x*(x*(-a+2.0) + (a-3.0)) + 1.0;
		else if (x < 2.0) 
			z = -a*x*x*x + 5.0*a*x*x - 8.0*a*x + 4.0*a;
		return z;
	}
	
	public static void main(String[] ar){
		double[][] data = {{0,1,2,3},
							{2,3,4,5},
							{2,3,4,5},
							{3,4,5,6}};
		printMatrix(data);
		System.out.println("1.3 1.3 "+getBicubicInterpolatedPixel(1.3, 1.3, data));
		System.out.println("1 1.5 "+getBicubicInterpolatedPixel(1.0, 1.5, data));
		System.out.println("1.5 1 "+getBicubicInterpolatedPixel(1.5, 1.0, data));
		System.out.println("0.5 0.5 "+getBicubicInterpolatedPixel(0.5, 0.5, data));
		System.out.println("2.3 2.0 "+getBicubicInterpolatedPixel(2.3, 2, data));
		double[][] variance = getVarianceImage(data,1);
		System.out.println("VarianceImage");
		printMatrix(variance);
	}
	
	public static void printMatrix(double[][] matrix){
		DecimalFormat f = new DecimalFormat("0.#");
		for (int x = 0; x< matrix.length;++x){
			for (int y = 0; y<matrix[x].length;++y){
				System.out.print(f.format(matrix[x][y])+"\t");
			}
			System.out.println();
		}
	}
	
}