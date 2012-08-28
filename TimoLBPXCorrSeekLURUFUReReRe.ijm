//Start imageJ with more memory java -jar ij.jar -Xmx2G

macro "LookStudyAnalysis"{
	setBatchMode(true);
	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\karsittuLuruFURe\\";
	dumpDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\luruFURe\\";
	visualDump = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\ijLuruFURe\\";
	analyseFiles(sourceDir,dumpDir,visualDump);
	
	setBatchMode(false);
}

	//FUNCTION TO ANALYSE FILES
	function analyseFiles(sourceDir,dumpDir,visualDump){
		files = getFileList(sourceDir);
		for (i = 0; i<files.length;++i){
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
			run("Seeded 3D Region Grow With LBP and seed searching with LBP", "pathtotemplate=C:/MyTemp/oma/Timon/tyo/SubchondralPilot/ijGrower/src/template/ templatefilename=master.raw templatemaskname=mask.raw width=290 height=200 maxdiff=1.5 secondgrow stdgrow lbplimit1=0.11 lbplimit2=0.11 greylimit1=1.5 greylimit2=2.25 gradientlimit1=1.0 gradientlimit2=1.0 result_dump_path="+dumpDir+file+".mat visual_result_path="+visualDump+file+".raw");
			/*
			run("3D Viewer");
			call("ij3d.ImageJ3DViewer.setCoordinateSystem", "false");
			call("ij3d.ImageJ3DViewer.add", "Region", "White", "Region", "50", "true", "true", "true", "2", "2");
			waitForUser("Click when done");
			waitForUser("Click when done");			
			*/
			run("Close All");
	}

