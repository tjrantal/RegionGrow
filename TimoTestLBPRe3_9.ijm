//Start imageJ with more memory java -jar ij.jar -Xmx2G

macro "LookStudyAnalysis"{
	setBatchMode(true);

	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittuRe\\"
	dumpDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\terveet\\"
	visualDump = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\ijTerveet\\"
	analyseFiles(sourceDir,dumpDir,visualDump);
	
	setBatchMode(false);
}

	//FUNCTION TO ANALYSE FILES
	function analyseFiles(sourceDir,dumpDir,visualDump){
		files = getFileList(sourceDir);
		for (i = 0; i<files.length;++i){
		//for (i = 3; i<files.length;++i){
		//for (i = 5; i<files.length;++i){
		//for (i = 9; i<files.length;++i){
			testi = File.isDirectory(sourceDir+files[i]);
			if (File.isDirectory(sourceDir+files[i])==1){ /*A folder*/
				folderName = replace(files[i],"/","\\");
				testi2 = sourceDir+folderName;
				IJ.log(testi2);
				analyseFiles(testi2,dumpDir,visualDump);
			}else{ /*A File*/
				analyseFile(sourceDir,files[i],dumpDir,visualDump);
				i = files.length+1;	/*Break the loop. Just the first file in each directory is considered*/
			}
		}
	}
	
	function analyseFile(sourceDir,file,dumpDir,visualDump){
			run("Image Sequence...", "open="+sourceDir+file+" number=19 starting=1 increment=1 scale=100 file=[] or=[] sort");
			run("Median...", "radius=2 stack");
			run("Seeded 3D Region Grow With LBP", "xlow=169 xhigh=250 ylow=370 yhigh=390 zlow=9 zhigh=12 maxdiff=1.5 secondgrow stdgrow lbplimit1=0.12 lbplimit2=0.12 greylimit1=1.5 greylimit2=2.5 gradientlimit1=1.5 gradientlimit2=1.25 result_dump_path="+dumpDir+file+".mat visual_result_path="+visualDump+file+".raw");
			/*
			run("3D Viewer");
			call("ij3d.ImageJ3DViewer.setCoordinateSystem", "false");
			call("ij3d.ImageJ3DViewer.add", "Region", "White", "Region", "50", "true", "true", "true", "2", "2");
			waitForUser("Click when done");
			*/
			run("Close All");
	}

