package MapSupport;

import enums.Science;
import enums.Terrain;

public class ScanMap {
	private MapTile[][] scanArray;
	private int edgeSize;
	private Coord centerPoint;
	
	public ScanMap(){
		this.scanArray = null;
		this.edgeSize = 0;
		this.centerPoint = null;		
	}
	
	public ScanMap(MapTile[][] scanArray, int size, Coord centerPoint){
		this.scanArray = scanArray;
		this.edgeSize = size;
		this.centerPoint = centerPoint;		
	}
	
	public MapTile[][] getScanMap(){
		return scanArray;
	}
	
	public Coord getcenterPoint(){
		return centerPoint;
	}
	
	public void debugPrintMap(){
		
		Science science;
		Terrain terrain;
		
		for(int k=0;k<edgeSize + 2;k++){System.out.print("--");}
		System.out.print("\n");
		for(int j= 0; j< edgeSize; j++){
			System.out.print("| ");
			for(int i= 0; i< edgeSize; i++){
				
				science = scanArray[i][j].getScience();
				terrain = scanArray[i][j].getTerrain();
				
				//check and print edge of map has first priority
				if(terrain.equals(Terrain.NONE)){
					System.out.print("XX");
					
				// next most important - print terrain and/or science locations
					//terrain and science
				} else if(!terrain.equals(Terrain.SOIL) && !science.equals(Science.NONE)){
					// both terrain and science
					
					System.out.print(terrain.getTerString() + science.getSciString());
					//just terrain
				} else if(!terrain.equals(Terrain.SOIL)){
					System.out.print(terrain.getTerString() + " ");
					//just science
				} else if(!science.equals(Science.NONE)){
					System.out.print(" " + science.getSciString());
					
				// if still empty check for rovers and print them
				} else if(scanArray[i][j].getHasRover()){
					System.out.print("[]");
					
				// nothing here so print nothing
				} else {
					System.out.print("  ");
				}
			}	
			System.out.print(" |\n");		
		}
		for(int k=0;k<edgeSize +2;k++){
			System.out.print("--");
		}
		System.out.print("\n");
	}
	
	public int getEdgeSize(){
		return edgeSize;
	}
}
