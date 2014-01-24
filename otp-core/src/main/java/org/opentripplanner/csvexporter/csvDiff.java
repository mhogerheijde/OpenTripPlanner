package org.opentripplanner.csvexporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class csvDiff {
	
	private File latestCsv = null;
	private File lastUploadedcsv = null;
		
	/**  dataLatestcsv: ArrayList of the latest uploaded CSV file. 
	 * 					represents vertex data when computeVertexdiff() method is called
	 * 					represents edge data when computeEdgediff() method is called
	 * 	 dataLatestuploadedCsv: ArrayList of the last uploaded CSV file
	 * 					represents vertex data when computeVertexdiff() method is called
	 * 					represents edge data when computeEdgediff() method is called
	 */
	
	public ArrayList<ArrayList<String>> dataLatestcsv = new ArrayList<ArrayList<String>>();
	public ArrayList<ArrayList<String>> dataLatestuploadedCsv = new ArrayList<ArrayList<String>>();
	
	/**csvDiff computed the difference between 
	 * the latest csv and last uploaded csv file
	 * displays the percentage of difference between the vertices, edges. 
	 */
	
	public csvDiff(){
		csvFiles csvfile = new csvFiles();
		latestCsv = csvfile.getLatestcsv();
		lastUploadedcsv = csvfile.getLastUplodedcsv();				
	}
	
	public ArrayList<String> computeVertexdiff(){
			
		ArrayList<String> diff = new ArrayList<String>();
				
		{File vertexFile = new File(latestCsv.getAbsolutePath()+"/"+latestCsv.getName()+"_vertices.csv");
		csvReader csvread = new csvReader();		
		dataLatestcsv = csvread.csvTostringArray(vertexFile);}
		
		{File vertexFile = new File(lastUploadedcsv.getAbsolutePath()+"/"+lastUploadedcsv.getName()+"_vertices.csv");
		csvReader csvread = new csvReader();		
		dataLatestuploadedCsv = csvread.csvTostringArray(vertexFile);}
		
		Collection<String> tempUpList = new ArrayList<String>();
		Collection<String> tempList = new ArrayList<String>();
				
		Collection<String> dataList = dataLatestcsv.get(0);
		Collection<String> dataUplist = dataLatestuploadedCsv.get(0);
		
		tempUpList.addAll(dataUplist);
		tempList.addAll(dataList);
		
		//computing the differences			
		if (dataUplist.size() < dataList.size()){			
			tempList.removeAll(dataUplist);									
		}
		else if(dataList.size() < dataUplist.size()){
			tempUpList.retainAll(tempList);
			tempList.removeAll(tempUpList);				
		}
		else{
			tempUpList.removeAll(tempList);
			tempList = tempUpList;
		}				
		diff.addAll(tempList);		
		return diff;					
	}

	public ArrayList<String> computeEdgediff(){
		
		ArrayList<String> diff = new ArrayList<String>();		
		
		{File vertexFile = new File(latestCsv.getAbsolutePath()+"/"+latestCsv.getName()+"_edges.csv");
		csvReader csvread = new csvReader();		
		dataLatestcsv = csvread.csvTostringArray(vertexFile);}
		
		{File vertexFile = new File(lastUploadedcsv.getAbsolutePath()+"/"+lastUploadedcsv.getName()+"_edges.csv");
		csvReader csvread = new csvReader();		
		dataLatestuploadedCsv = csvread.csvTostringArray(vertexFile);}
		
		Collection<String> tempUpList = new ArrayList<String>();
		Collection<String> tempList = new ArrayList<String>();
				
		Collection<String> dataList = dataLatestcsv.get(0);
		Collection<String> dataUplist = dataLatestuploadedCsv.get(0);
		
		tempUpList.addAll(dataUplist);
		tempList.addAll(dataList);
		
		//computing the differences			
		if (dataUplist.size() < dataList.size()){			
			tempList.removeAll(dataUplist);									
		}
		else if(dataList.size() < dataUplist.size()){
			tempUpList.retainAll(tempList);
			tempList.removeAll(tempUpList);				
		}
		else{
			tempUpList.removeAll(tempList);
			tempList = tempUpList;
		}				
		
		diff.addAll(tempList);		
		return diff;					
	}

}
