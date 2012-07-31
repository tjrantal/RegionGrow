/*Bicubic interpolation copied from imageJ imageProcessor.*/

/*Some filtering functions reproduced here to enable using the code without ImageJ*/

package	ijGrower;

public class Filters{
	
	/** This method is from Chapter 16 of "Digital Image Processing:
		An Algorithmic Introduction Using Java" by Burger and Burge
		(http://www.imagingbook.com/). */
	public static double getBicubicInterpolatedPixel(double x0, double y0, double[][] data) {
		int u0 = (int) Math.floor(x0);	//use floor to handle negative coordinates too
		int v0 = (int) Math.floor(y0);
		int width = data[0].length;
		int height = data.length;
		if (u0<1 || u0>width-3 || v0< 1 || v0>height-3)
			return 0; /*Return zero for points outside the image*/
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
		double[][] data = {{0,1,2,3},{1,2,3,4},{2,3,4,5},{3,4,5,6}};
		System.out.println(getBicubicInterpolatedPixel(1.3, 1.3, data));
	}
	
}