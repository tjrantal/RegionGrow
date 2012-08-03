/*
	Created by Timo Rantalainen
*/

package ijGrower;

/*Next Pixel for pixel queue, comparable enables always getting the highest value*/
public class NextPixelLBP extends NextPixel {

	public NextPixelLBP(double cost, int[] coordinates){
		super(cost,coordinates);
	}
	
	public int compareTo(NextPixelLBP other){
		if( cost > other.cost){
			return -1;
		}else{ 
			if( cost < other.cost){ 
				return +1;
			}else{
				return 0;
			}
		}
	}

}
