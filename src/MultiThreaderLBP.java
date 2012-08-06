	/*Multithreading*/
	package	ijGrower;

public class MultiThreaderLBP extends Thread{
		public LBP lbp;
		public byte[][] lbpImage;
		private double[][] tempData;
		public int d;
		/*Costructor*/
		public MultiThreaderLBP(double[][] tempData,int d){
			lbp = new LBP(16,2);
			this.tempData = tempData;
			this.d = d;
		}

		
		public void run(){
			lbpImage = lbp.getLBP(tempData);
		}
}