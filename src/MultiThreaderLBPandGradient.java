	/*Multithreading*/
	package	ijGrower;

public class MultiThreaderLBPandGradient extends Thread{
		public LBP lbp;
		public byte[][] lbpImage;
		public double[][] gradientImage;
		private double[][] tempData;
		public int d;
		/*Costructor*/
		public MultiThreaderLBPandGradient(double[][] tempData,int d){
			lbp = new LBP(16,2);
			this.tempData = tempData;
			this.d = d;
		}

		
		public void run(){
			lbpImage = lbp.getLBP(tempData);
			gradientImage = Filters.getGradientImage(tempData);
		}
}