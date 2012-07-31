/*
The software is licensed under a Creative Commons Attribution 3.0 Unported License.
Copyright (C) 2012 Timo Rantalainen
*/

/*
A class to dump a 3D byte or double array into a .mat file
Mat file format obtained from  http://www.mathworks.com/help/pdf_doc/matlab/matfile_format.pdf on 12th of march 2012

A minimum of three calls is required to use the class:
File is opened and header written, when constructed.
Arrays are written with the write array methods
Finally, the file needs to be closed with the closeFile method.
N.B. Data will be written to memory in between (i.e. each write array method writes to memory in between), which may be a problem for big datasets...
*/

package	ijGrower;
import java.io.*;
import java.util.Arrays;
import java.lang.reflect.Array;
public class WriteMat{
	
	FileOutputStream writer = null;
	
	/*Matlab data type tags*/	
	final static int miINT8 = 1;
	final static int miUINT8 = 2;
	final static int miINT16 = 3;
	final static int miUINT16 = 4;
	final static int miINT32 = 5;
	final static int miUINT32 = 6;
	final static int miDOUBLE = 9;
	final static int miMATRIX = 14;
	
	/*Matlab Array types*/
	final static int mxCELL_CLASS	= 1;
	final static int mxSTRUCT_CLASS = 2;
	final static int mxOBJECT_CLASS = 3;
	final static int mxCHAR_CLASS	= 4;
	final static int mxSPARSE_CLASS = 5;
	final static int mxDOUBLE_CLASS = 6;
	final static int mxSINGLE_CLASS = 7;
	final static int mxINT8_CLASS 	= 8;
	final static int mxUINT8_CLASS 	= 9;
	final static int mxINT16_CLASS 	= 10;
	final static int mxUINT16_CLASS = 11;
	final static int mxINT32_CLASS 	= 12;
	final static int mxUINT32_CLASS = 13;
	final static int mxINT64_CLASS 	= 14;
	final static int mxUINT64_CLASS = 15;
	
	/*
		Constructor
		@param fileName	Filename of the file that the data will be written to. Opens the file as FileOutputStream and writes the header.
	*/
	
	public WriteMat(String fileName){
		try{
			writer = new FileOutputStream(fileName);
			writeHeader();
		}catch (Exception err){System.out.println("Couldn't open "+err.toString());}
	}
	
	/*Writes the header. File will be written as Little endian*/
	private void writeHeader(){		
		byte[] headerData = new byte[128];
		String text = "Timo's mat dump";
		try{
			byte[] textToWrite = text.getBytes("US-ASCII");
			//First four bytes need to be non-zero
			for (int i = 0;i<textToWrite.length;++i){		
				headerData[i] = textToWrite[i];
			}
		}catch (Exception err){System.out.println("Couldn't find US-ASCII encoding");}
		//Set flags
		long flag1;
		flag1 = 0x0100;
		headerData = putBytes(headerData,flag1,124,2);
		/*Check whether this needs to be MI or IM (IM = LE)*/
		headerData[126] = (byte) 73;	//I
		headerData[127] = (byte) 77;	//M
		try{
			writer.write(headerData);
		}catch (Exception err){System.out.println("Couldn't write "+err.toString());}
	}
	
	public static int getDim(Object array) {
		int dim = 0;
		Class cls = array.getClass();
		while (cls.isArray()) {
			++dim;
			cls = cls.getComponentType();
		}
		return dim;
	}
	
	
	public void writeArray(double[][][] data, String varName){
		byte[] dataElementHeader = createElement(varName,data);
		try{
			writer.write(dataElementHeader);	/*writeDataElementHeader*/
			/*Write the array data*/
			/*DATA*/
			byte[] tempArray = new byte[8];
			for (int d = 0; d < data[0][0].length; ++d) {
				for (int c = 0;c<data[0].length;++c){
					for (int r = 0;r<data.length;++r){
					
						tempArray = putDouble(tempArray,data[r][c][d],0);	/*Matlab data needs to be written column at a time*/
						writer.write(tempArray);
					}
				}
			}
		}catch (Exception err){System.out.println("Couldn't write array "+err.toString());}
	}
	
	public void writeArray(byte[][][] data, String varName){
		byte[] dataElementHeader = createElement(varName,data);
		try{
			writer.write(dataElementHeader);	/*writeDataElementHeader*/
			/*Write the array data*/
			/*DATA*/
			for (int d = 0; d < data[0][0].length; ++d) {
				for (int c = 0;c<data[0].length;++c){
					for (int r = 0;r<data.length;++r){		/*Matlab data needs to be written column at a time*/
						writer.write(data[r][c][d]);
					}
				}
			}
			int dataSize = data.length*data[0].length*data[0][0].length;
			if (dataSize%8 != 0){
				byte[] padding  = new byte[8-dataSize%8];
				writer.write(padding);
			}
			printMatrix(data);
			
		}catch (Exception err){System.out.println("Couldn't write array "+err.toString());}
	}


	private byte[] createElement(String varName,Object data){
		long dataSize = 0;
		long paddedDataArraySize = 0;
		int sizeOfDataElement = 1;
		int dataArraySize = 0;
		int[] arrayDimensions = new int[3];
		if (data instanceof double[][][]){
			sizeOfDataElement = 8;
			dataArraySize = ((double[][][]) data).length*((double[][][]) data)[0].length*((double[][][]) data)[0][0].length*sizeOfDataElement;
			arrayDimensions[0] = ((double[][][])data).length;
			arrayDimensions[1] = ((double[][][])data)[0].length;
			arrayDimensions[2] = ((double[][][])data)[0][0].length;
			
			
		}
		if (data instanceof byte[][][]){
			dataArraySize = ((byte[][][]) data).length*((byte[][][]) data)[0].length*((byte[][][]) data)[0][0].length*sizeOfDataElement;
			arrayDimensions[0] = ((byte[][][])data).length;
			arrayDimensions[1] = ((byte[][][])data)[0].length;
			arrayDimensions[2] = ((byte[][][])data)[0][0].length;
		}
		
		
		
		int dimensionsBytes = getDim(data)*4;
		if (dimensionsBytes%8 != 0){
			dimensionsBytes+=8-dimensionsBytes%8;
		}
		
		int varNameLength = varName.length();
		if (varNameLength%8 != 0){
			varNameLength+=8-varName.length()%8;
		}
		
		paddedDataArraySize = dataArraySize;
		if (paddedDataArraySize%8 != 0){
			paddedDataArraySize+=8-paddedDataArraySize%8;
		}
		
		System.out.println("Dimensions "+getDim(data));
		

		/*DEBUG*/
		for (int d = 0;d<getDim(data);++d){
			System.out.println("d"+d+" "+arrayDimensions[d]);
		}

		byte[] dataElementHeader = new byte[8+(8+8)+(8+dimensionsBytes)+(8+varNameLength)+8];
		dataSize = dataElementHeader.length-8+paddedDataArraySize; //Check this size...
		int offset = 0;
		long dataType = 0;
		

		
		/*Array miMATRIX*/
			/*tag*/
			dataType = miMATRIX;	//miMATRIX
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			dataElementHeader= putBytes(dataElementHeader,dataSize,offset,4);
			offset+=4;

		/*Array flags  2*4+2*4 bytes*/ 
			/*tag*/
			dataType = miUINT32;	//miUINT32
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			dataSize = 2*4; //Check this size...
			dataElementHeader= putBytes(dataElementHeader,dataSize,offset,4);
			offset+=4;
			/*data*/
			/*Data type and flags (flags can be 0...)*/
			if (data instanceof double[][][]){
				dataType = mxDOUBLE_CLASS;	//Double Flag
			}
			if (data instanceof byte[][][]){
				dataType = mxINT8_CLASS;	//Double Flag
			}
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			//Insert 0s for undefined
			dataElementHeader= putBytes(dataElementHeader,0,offset,4);
			offset+=4;
		/*Dimension array 2*4+4*dims*/
			dataType = miINT32;	//miINT32
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			dataSize = getDim(data)*4; //Check this size...
			dataElementHeader= putBytes(dataElementHeader,dataSize,offset,4);
			offset+=4;
			/*data*/
			/*rows x cols x depth*/
			dataType = arrayDimensions[0];	//rows
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			dataType = arrayDimensions[1];	//cols
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			dataType = arrayDimensions[2];	//depth
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			/*Add the padding to the offset...*/
			offset+=dimensionsBytes-getDim(data)*4; 
		/*Array name 16+stringLength*/
			dataType = miINT8;	//miINT8
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			dataSize = varName.length(); //Check this size...
			dataElementHeader= putBytes(dataElementHeader,dataSize,offset,4);
			offset+=4;
			/*data*/
			/*VarName*/
			try{
				byte[] tagData = varName.getBytes("US-ASCII"); //{0x21,0x22,0x23,0x24,0x25,0x26,0x27,0x28};
				dataElementHeader = putString(dataElementHeader,tagData,offset);
				System.out.println("tD lenght "+tagData.length+" varNameL "+varNameLength);
				offset = offset+varNameLength;
			}catch (Exception err){System.out.println("Couldn't find US-ASCII encoding");}
		/*Array 16+10*8*/
			/*Data type*/
			if (data instanceof double[][][]){
				dataType = miDOUBLE;	//miDOUBLE
			}
			if (data instanceof byte[][][]){
				dataType = miINT8;	//miDOUBLE
			}
			
			dataElementHeader= putBytes(dataElementHeader,dataType,offset,4);
			offset+=4;
			/*Data size*/
			dataElementHeader= putBytes(dataElementHeader,dataArraySize,offset,4);
			offset+=4;
		return dataElementHeader;
	}
	
	public void closeFile(){
		try{
			writer.close();
		}catch (Exception err){System.out.println("Couldn't close "+err.toString());}
	}
	
	private byte[] putBytes(byte[] fileData, long input, int offset,int noOfBytes){
		//System.out.println("Put Long");
		for (int i = 0; i < noOfBytes;++i){
			short temp =(short) ((input & (255L <<(8*i)))>>8*i);
			//System.out.println(temp);
			fileData[offset+i]  = (byte) temp;
		}
		return fileData;
	}
	
	private byte[] putString(byte[] fileData, byte[] input, int offset){
		//System.out.println("Put Long");
		for (int i = 0; i < input.length;++i){
			fileData[offset+i]  = input[i];
		}
		return fileData;
	}
	
	private byte[] putDouble(byte[] fileData,double input, int offset){
		//System.out.println("Put Double");
		for (int i = 0; i < 8;++i){
			short temp =(short) ((Double.doubleToRawLongBits(input) & (255L <<(8*i)))>>8*i);
			//System.out.println(temp);
			fileData[(int) offset+i]  = (byte) temp;
		}
		return fileData;
	}
	
	/*For testing*/
	public static void main(String[] ar){
		WriteMat wm = new WriteMat("C:\\MyTemp\\oma\\Timon\\tyo\\SubchondralPilot\\matlabDump\\testDump.mat");
		byte[][][] testData ={{{0,1,2},{3,4,5},{6,7,8},{6,7,8}},{{9,10,11},{12,13,14},{15,16,17},{12,13,14}}};
		System.out.println("d "+testData.length+" r "+testData[0].length+" w "+testData[0][0].length);
		wm.writeArray(testData,"Test");
		wm.closeFile();
	}
	
	public void printMatrix(byte[][][] arrayIn){
		for (int d = 0; d <arrayIn.length;++d){
			System.out.println("Slice "+d);
			for (int r = 0; r<arrayIn[d].length;++r){
				for (int c = 0; c<arrayIn[d][r].length;++c){
					System.out.print((int) arrayIn[d][r][c]+"\t");
				}
				System.out.print("\n");
			}
			System.out.print("\n");
		}
		System.out.println("D "+arrayIn.length+" R "+arrayIn[0].length+" C "+arrayIn[0][0].length);
	}
}