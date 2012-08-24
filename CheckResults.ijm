//Start imageJ with more memory java -jar ij.jar -Xmx2G

macro "LookStudyAnalysis"{
	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\ijTerveet\\"
	//sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\ijLuru\\"
	sourceDir = "C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\ijTerveetRe\\"
	analyseFiles(sourceDir);
}

	//FUNCTION TO ANALYSE FILES
	function analyseFiles(sourceDir){
		files = getFileList(sourceDir);
		for (i = 0; i<files.length;++i){
			testi = File.isDirectory(sourceDir+files[i]);
			if (File.isDirectory(sourceDir+files[i])==1){ /*A folder*/
				folderName = replace(files[i],"/","\\");
				testi2 = sourceDir+folderName;
				IJ.log(testi2);
				analyseFiles(testi2);
			}else{ /*A File*/
				analyseFile(sourceDir,files[i]);
			}
		}
	}
	
	function analyseFile(sourceDir,file){
		//IJ.log(sourceDir+file);
		run("Raw...", "open="+sourceDir+file+" image=[16-bit Signed] width=512 height=512 offset=0 number=19 gap=0");
		waitForUser("Click when done");
		run("Close All");
	}

