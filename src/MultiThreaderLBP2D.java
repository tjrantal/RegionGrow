	/*Multithreading*/
	package	ijGrower;

public class MultiThreaderLBP2D extends Thread{
		public RegionGrow2D r2d;
		public int r;
		private int postErodeReps;
		private int preErodeReps;
		private boolean preErode;
		/*Costructor*/
		public MultiThreaderLBP2D(RegionGrow2D r2d, int r,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			preErode = false;
		}
		/*Costructor with pre-erode*/
		public MultiThreaderLBP2D(RegionGrow2D r2d, int r,int preErodeReps,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.preErodeReps = preErodeReps;
			preErode = true;
		}
		
		public void run(){
			if (preErode){
				for (int i = 0;i<preErodeReps;++i){
					r2d.erodeMask();	//Remove extra stuff from sagittal growing...
				}
			}
			if (r2d.maskHasPixels()){
				r2d.growRegionLBP();
				r2d.fillVoids(); //Fill void
				for (int i = 0; i<postErodeReps;++i){
					r2d.erodeMask();	/*Try to remove spurs...*/
				}
			}
		}
}