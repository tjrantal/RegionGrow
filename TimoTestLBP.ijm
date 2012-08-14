//Start imageJ with more memory java -jar ij.jar -Xmx2G

macro "LookStudyAnalysis"{
//	setBatchMode(true);
	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\"
//	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittuLuru\\"
//	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh1\\"
	analyseFiles(sourceDir);
	
	//setBatchMode(false);
}

	//FUNCTION TO ANALYSE FILES
	function analyseFiles(sourceDir){
		files = getFileList(sourceDir);
		for (i = 0; i<files.length;++i){
		//for (i = 3; i<files.length;++i){
			testi = File.isDirectory(sourceDir+files[i]);
			if (File.isDirectory(sourceDir+files[i])==1){ /*A folder*/
				folderName = replace(files[i],"/","\\");
				testi2 = sourceDir+folderName;
				IJ.log(testi2);
				analyseFiles(testi2);
			}else{ /*A File*/
				analyseFile(sourceDir,files[i]);
				i = files.length+1;	/*Break the loop. Just the first file in each directory is considered*/
			}
		}
	}
	
	function analyseFile(sourceDir,file){
			run("Image Sequence...", "open="+sourceDir+file+" number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
			run("Median...", "radius=2 stack");
			run("Seeded 3D Region Grow With LBP", "xlow=169 xhigh=250 ylow=370 yhigh=390 zlow=9 zhigh=12 maxdiff=1.5 secondgrow stdgrow lbplimit=0.12 greylimit1=1.5 greylimit2=2.5");
			run("3D Viewer");
			call("ij3d.ImageJ3DViewer.setCoordinateSystem", "false");
			call("ij3d.ImageJ3DViewer.add", "Region", "White", "Region", "50", "true", "true", "true", "2", "2");
			waitForUser("Click when done");
			run("Close All");
	}



//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh1\\18834323 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh2\\18930620 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh3\\19121225 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh4\\32403766 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh5\\33549498 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh6\\33652533 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh7\\35909635 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh8\\01022313 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh9\\20505036 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittu\\kh10\\24176472 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");

//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittuLuru\\AE270654\\44861314 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
//run("Image Sequence...", "open=C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittuLuru\\AL130549\\41332348 number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");


/*Run the analysis*/
//run("Median...", "radius=2 stack");
//run("Seeded 3D Region Grow With LBP", "xlow=169 xhigh=250 ylow=370 yhigh=390 zlow=9 zhigh=12 maxdiff=150 stdgrow");
//run("Seeded 3D Region Grow With LBP", "xlow=169 xhigh=250 ylow=370 yhigh=390 zlow=9 zhigh=12 maxdiff=150 secondgrow stdgrow ");


/*Run IJ 3D Viewer*/

//run("3D Viewer");
//call("ij3d.ImageJ3DViewer.setCoordinateSystem", "false");
//call("ij3d.ImageJ3DViewer.add", "Region", "White", "Region", "50", "true", "true", "true", "2", "2");
