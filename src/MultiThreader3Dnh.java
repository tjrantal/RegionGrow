	/*Multithreading*/
	package	ijGrower;

public class MultiThreader3Dnh extends Thread{
		public RegionGrow2D3DNeighbourhood r2d;
		public int r;
		private int postErodeReps;
		private int preErodeReps;
		private boolean preErode;
		private boolean doFillVoids;
		/*Costructor*/
		public MultiThreader3Dnh(RegionGrow2D3DNeighbourhood r2d, int r,int postErodeReps){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			doFillVoids = true;
			preErode = false;
		}
		/*Costructor with pre-erode*/
		public MultiThreader3Dnh(RegionGrow2D3DNeighbourhood r2d, int r){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.preErodeReps = preErodeReps;
			doFillVoids = true;
			preErode = true;
		}
		/*Costructor*/
		public MultiThreader3Dnh(RegionGrow2D3DNeighbourhood r2d, int r,int postErodeReps,boolean doFillVoids){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.doFillVoids = doFillVoids;
			preErode = false;
		}
		/*Costructor with pre-erode*/
		public MultiThreader3Dnh(RegionGrow2D3DNeighbourhood r2d, int r,int preErodeReps,int postErodeReps,boolean doFillVoids){
			this.r2d = r2d;
			this.r = r;
			this.postErodeReps = postErodeReps;
			this.preErodeReps = preErodeReps;
			this.doFillVoids = doFillVoids;
			preErode = true;
		}
		
		public void run(){
				r2d.growRegion();
				r2d.fillVoids(); //Fill void
		}
}