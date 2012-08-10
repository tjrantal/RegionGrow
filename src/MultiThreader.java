	/*Multithreading*/
	package	ijGrower;

public class MultiThreader extends Thread{
		public RegionGrow2D r2d;
		public int r;
		private int postErodeReps;
		private int preErodeReps;
		private boolean preErode;
		private boolean doFillVoids;
		/*Costructor*/
		public MultiThreader(RegionGrow2D r2d, int r,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			doFillVoids = true;
			preErode = false;
		}
		/*Costructor with pre-erode*/
		public MultiThreader(RegionGrow2D r2d, int r,int preErodeReps,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.preErodeReps = preErodeReps;
			doFillVoids = true;
			preErode = true;
		}
		/*Costructor*/
		public MultiThreader(RegionGrow2D r2d, int r,int postErodeReps,boolean doFillVoids){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.doFillVoids = doFillVoids;
			preErode = false;
		}
		/*Costructor with pre-erode*/
		public MultiThreader(RegionGrow2D r2d, int r,int preErodeReps,int postErodeReps,boolean doFillVoids){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.preErodeReps = preErodeReps;
			this.doFillVoids = doFillVoids;
			preErode = true;
		}
		
		public void run(){
			if (preErode){
				for (int i = 0;i<preErodeReps;++i){
					r2d.erodeMask();	//Remove extra stuff from sagittal growing...
				}
			}
			if (r2d.maskHasPixels()){
				r2d.growRegion();
				if (doFillVoids){
					r2d.fillVoids(); //Fill void
				}
				for (int i = 0; i<postErodeReps;++i){
					r2d.erodeMask();	/*Try to remove spurs...*/
				}
			}
		}
}