	/*Multithreading*/
	package	ijGrower;

public class MultiThreaderXCorr extends Thread{
		private double[][] tempData;
		private double[][] sliceData;
		public double[][] xcorr;
		public int d;
		/*Costructor*/
		public MultiThreaderXCorr(double[][] sliceData,double[][] tempData,int d){
			this.sliceData = sliceData;
			this.tempData = tempData;
			this.d = d;
		}

		
		public void run(){
			xcorr = Filters.xcorr(sliceData,tempData);
		}
}