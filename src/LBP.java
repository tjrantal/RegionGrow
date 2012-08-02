/*A class for calculating rotation invariant uniform local binary pattern in
	circular neighbourhood of up to 127 neighbours and with integer values radius.
*/

/*Bicubic interpolation copied from imageJ imageProcessor.*/

/*
	LBP ported from Oulu machine vision group lbp code (http://www.cse.oulu.fi/CMV/Downloads).
	
*/

package	ijGrower;

import java.text.DecimalFormat;	/*For debugging*/

public class LBP{
	private byte[] mapping;
	private double[][] neighbourhood;
	private int radius;
	private int samples;
	
	public LBP(int samples, int radius){
		this.samples	= samples;
		this.radius		= radius;
		mapping = getMapping(samples);
		System.out.println("Mapping length "+mapping.length+" samples "+samples);
		neighbourhood = getCircularNeighbourhood(radius,samples);
		
	}
	
	public byte[][] getLBP(double[][] data){
		int width = data.length;
		int height = data[0].length;
		byte[][] lbpSlice = new byte[width][height];
		/*Calculate the lbp*/
		int[] coordinates = new int[2];
		for (int i = 0+radius;i<width-radius;++i){
			for (int j = 0+radius;j<height-radius;++j){
				coordinates[0] = i;
				coordinates[1] = j;
				//System.out.println("source x "+coordinates[0]+" y "+coordinates[1]);
				lbpSlice[i][j] = lbpBlock(data,coordinates);
			}
		}
		
		return lbpSlice;
	}
	
	private byte lbpBlock(double[][] data,int[] coordinates){
		int lbpValue = 0;
		double x = (double) coordinates[0];
		double y = (double) coordinates[1];
		for (int i = 0; i<neighbourhood.length;++i){
			lbpValue = data[(int) x][(int) y] > getBicubicInterpolatedPixel(x+neighbourhood[i][0],y+neighbourhood[i][1],data)+3.0 ? lbpValue | (1 << i) : lbpValue & ~(1 << i);
		}
		return mapping[lbpValue];
	}

	/*Mapping to rotation invariant uniform patterns: riu2 in getmapping.m*/	
	private static byte[] getMapping(int samples){
		int bitMaskLength = (int) (Math.pow(2.0,(double) samples));
		byte[] table = new byte[bitMaskLength];
		int j;
		int sampleBitMask = 0;
		for (int i = 0;i<samples;++i){
			sampleBitMask |= 1<<i;
		}
		
		int numt;
		for (int i = 0;i<bitMaskLength;++i){
			j = ((i<<1) & sampleBitMask); //j = bitset(bitshift(i,1,samples),1,bitget(i,samples)); %rotate left
			j = (i>>(samples-1)) >0 ? j | 1: j & ~1;	//Set first bit to one or zero
			numt = 0;
			for (int k = 0;k<samples;++k){
				numt+= (((i^j)>>k) & 1);
			}
			
			if (numt <= 2){
				for (int k = 0;k<samples;++k){
					table[i]+= (i>>k) & 1;
				}
			}else{
				table[i] = (byte) (samples+1);
			}
		}
		return table;
	}
	
	/*Neighbourhood coordinates relative to pixel of interest*/
	private static double[][] getCircularNeighbourhood(int radius,int samples){
		double[][] samplingCoordinates = new double[samples][2];
		final double angleIncrement = Math.PI*2.0/(double) samples;
		for (int n = 0;n <samples; ++n){
			samplingCoordinates[n][0] = ((double) radius)*Math.cos(((double)n)*angleIncrement);
			samplingCoordinates[n][1] = ((double) radius)*Math.sin(((double)n)*angleIncrement);
		}
		return samplingCoordinates;
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
							
		double[][] data = {{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,5,5,1,1,1,1,1,1},
							{1,1,1,1,1,5,5,5,1,1,1,1,1},
							{1,1,1,1,1,5,5,5,5,1,1,1,1},
							{1,1,1,1,1,5,5,5,5,5,1,1,1},
							{1,1,1,1,1,5,5,5,5,5,1,1,1},
							{1,1,1,1,1,5,5,5,5,5,1,1,1},
							{1,1,1,1,1,5,5,5,5,5,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,1,5,1,1,1,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1},
							{1,1,1,1,1,1,1,1,1,1,1,1,1}};
		printMatrix(data);
		
		LBP lbp = new LBP(16,2);
		System.out.println("Neighbourhood");
		printMatrix(lbp.neighbourhood);
		byte[][] resultLBP = lbp.getLBP(data);
		
		System.out.println("VarianceImage");
		printMatrix(resultLBP);
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
	
	public static void printMatrix(byte[][] matrix){
		for (int x = 0; x< matrix.length;++x){
			for (int y = 0; y<matrix[x].length;++y){
				System.out.print(matrix[x][y]+"\t");
			}
			System.out.println();
		}
	}
	
}