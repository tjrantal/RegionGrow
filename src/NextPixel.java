/*
	Created by Timo Rantalainen
*/

package ijGrower;

/*Next Pixel for pixel queue, comparable enables always getting the smallest value*/
public class NextPixel implements Comparable<NextPixel> {
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
