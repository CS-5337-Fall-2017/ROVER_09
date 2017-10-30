package MapSupport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import communicationInterface.CommunicationHelper;
import enums.Science;
import enums.Terrain;

public class PlanetMap {
	private MapTile[][] planetMap;
	// width is number of columns is xloc, height is number of rows is yloc
	private int mapWidth;
	private int mapHeight;
	private Coord startPosCoord;
	private Coord targetPosCoord;
	public static final int START_LOCATION_SIZE = 7;
	public static final int TARGET_LOCATION_SIZE = 7;
	
	public PlanetMap(){
		this.mapHeight = 0;
		this.mapWidth = 0;
		this.planetMap = null;
		this.startPosCoord = null;
		this.targetPosCoord = null;
	}
	
	
	public PlanetMap(int width, int height){
		this.mapHeight = height;
		this.mapWidth = width;
		this.planetMap = new MapTile[width][height];
		for(int j=0;j<height;j++){
			for(int i=0;i<width;i++){
				this.planetMap[i][j] = new MapTile();
			}
		}
		this.startPosCoord = new Coord(0, 0);
		this.targetPosCoord = new Coord(0, 0);
	}
	
	public PlanetMap(int width, int height, Coord startPos, Coord targetPos){
		this.mapHeight = height;
		this.mapWidth = width;
		this.planetMap = new MapTile[width][height];
		for(int j=0;j<height;j++){
			for(int i=0;i<width;i++){
				this.planetMap[i][j] = new MapTile();
			}
		}
		this.startPosCoord = startPos;
		this.targetPosCoord = targetPos;
	}
	
	public PlanetMap(String filename){
		// TODO add ability to instantiate from JSON or binary file
	}
	
	// creates a PlanetMap from JSONArray that is received from server
	public PlanetMap(JSONArray data, Coord start, Coord target) {
		
		Map<Coord, MapTile> tileMap = new HashMap<Coord, MapTile>();
		
		int maxX = 0;
		int maxY = 0;
		int x = 0;
		int y = 0;
		
		for (Object o : data) {
			
			JSONObject jsonObj = (JSONObject) o;
			
			x = (int) (long)jsonObj.get("x");
			y = (int) (long)jsonObj.get("y");
			
			if ((x < 0) || (y < 0)) continue; // ingores negative index assuming all negative are unreachable NONE type
			
			
			Coord coord = new Coord(x,y);

			MapTile tile = CommunicationHelper.convertToMapTile(jsonObj);
			
			if (!tile.getTerrain().equals(Terrain.NONE)) {
				if (x > maxX) maxX = x;
				if (y > maxY) maxY = y;
			}
			
			tileMap.put(coord, tile);
		}
		
		this.startPosCoord = start;
		this.targetPosCoord = target;
	
		// mostly used to build a local map to run search to target
		// will build map big enough to run search for target
		if (target.xpos > maxX) maxX = target.xpos;
		if (target.ypos > maxY) maxY = target.ypos;
		
		this.mapWidth = maxX + 1; // 0 index must add one
		this.mapHeight = maxY + 1; // 0 index must add one
		
		this.planetMap = new MapTile[mapWidth][mapHeight];
		
		MapTile unexploredTile = new MapTile(1);
		
		for(int j = 0; j< mapHeight; j++){
			for(int i = 0; i< mapWidth; i++){
				this.planetMap[i][j] = unexploredTile; // all tiles start off as unexplored
			}
		}
		
		// explored tiles are filled in from the tileMap derived from the json
		
		MapTile tile;
		Coord coord;
		
		for(Map.Entry<Coord, MapTile> entry : tileMap.entrySet()) {
			coord = entry.getKey();
			tile = entry.getValue();
			
			if (coord.xpos >= mapWidth || coord.xpos < 0
					|| coord.ypos >= mapHeight || coord.ypos < 0) continue;
			
			this.planetMap[coord.xpos][coord.ypos] = tile;
		}
	}
	
	
	public PlanetMap(PlanetMap planetMapIn) {
		this.planetMap = planetMapIn.planetMap.clone();
		this.mapWidth = planetMapIn.mapWidth;
		this.mapHeight = planetMapIn.mapHeight;
		this.startPosCoord = planetMapIn.startPosCoord;
		this.targetPosCoord = planetMapIn.targetPosCoord;
	}

	public void setTile(MapTile tile, int xloc, int yloc){
		this.planetMap[xloc][yloc] = tile;
	}
	
	// adds missing rover data from ScanMap to PlanetMap
	public void addScanDataMissing(ScanMap scan){
		
		MapTile[][] scanMap = scan.getScanMap();
		
		Coord scanCenter = scan.getcenterPoint();
		
		int x;
		int y = scanCenter.ypos - 3;
		
		for(int j = 0;j < scanMap.length; j++, y++) {
			
			x = scanCenter.xpos - 3;
			
			for(int i = 0; i < scanMap[0].length; i++, x++) {
				
				if(x < 0 || y < 0) continue;
				if(x > mapWidth - 1 || y > mapHeight - 1) continue;
				
				if(x == startPosCoord.xpos && y == startPosCoord.ypos) continue;
				
				if(scanMap[i][j].getHasRover()) this.planetMap[x][y].setHasRoverTrue();
			}	
		}
	}
	
	public MapTile getTile(Coord coord){
		return this.planetMap[coord.xpos][coord.ypos];
	}
	
	public MapTile getTile(int xloc, int yloc){
		return this.planetMap[xloc][yloc];
	}
	
	// Generates and returns a local scanMap to the rover; assumes edge size is an odd number
	public ScanMap getScanMap(Coord coord, int edgeSize, RoverLocations rloc, ScienceLocations sciloc){
		int startx = coord.xpos - (edgeSize -1)/2;
		int starty = coord.ypos - (edgeSize -1)/2;
		MapTile aTile;
		MapTile[][] tMap = new MapTile[edgeSize][edgeSize];
		
		for(int j= 0; j< edgeSize; j++){
			for(int i= 0; i< edgeSize; i++){
				// Checks if location value is off the edge of the planetMap
				if((i + startx) < 0 || (i + startx) >= mapWidth || (j + starty) < 0 || (j + starty) >= mapHeight){
					aTile = new MapTile(0); // makes a MapTile with terrain = NONE
				} else {					
					// It is important to instantiate a new map tile to prevent
					// passing the tile by reference and corrupting the original planetMap
					aTile = planetMap[i + startx][j + starty].getCopyOfMapTile();
				}
				Coord tempCoord = new Coord(i + startx, j + starty);
				
				// check and add rover to tile
				if(rloc.containsCoord(tempCoord)){
					aTile.setHasRoverTrue();
				}
				
				// check and add Science if on map
				if(sciloc.checkLocation(tempCoord)){
					aTile.setScience(sciloc.scanLocation(tempCoord));
				}
				tMap[i][j] = aTile;
			}	
		}
		return new ScanMap(tMap, edgeSize, coord);
	}
	
	public int getWidth(){
		return this.mapWidth;
	}
	
	public int getHeight(){
		return this.mapHeight;
	}
	
	public Coord getStartPosition(){
		return this.startPosCoord;
	}
	
	public void setStartPosition(Coord start){
		this.startPosCoord = start;
	}
	
	public Coord getTargetPosition(){
		return this.targetPosCoord;
	}
	
	public void setTargetPosition(Coord target){
		this.targetPosCoord = target;
	}
	
	
	/*
	 * These are only used for testing and development
	 */
	public void loadExampleTestPlanetMapTerrain(){
		// temporary use for creating planet terrain for testing
		
		this.mapHeight = 40;
		this.mapWidth = 40;
		this.planetMap = new MapTile[mapWidth][mapHeight];
		for(int j=0;j<mapHeight;j++){
			for(int i=0;i<mapWidth;i++){
				this.planetMap[i][j] = new MapTile();
			}
		}
		
		this.planetMap[7][7] = new MapTile("R"); 
		this.planetMap[7][8] = new MapTile("R"); 
		this.planetMap[8][7] = new MapTile("R"); 
		this.planetMap[8][8] = new MapTile("R");
		
		this.planetMap[15][16] = new MapTile("R"); 
		this.planetMap[15][17] = new MapTile("R"); 
		this.planetMap[15][18] = new MapTile("R"); 
		this.planetMap[15][19] = new MapTile("R"); 
		this.planetMap[14][18] = new MapTile("R"); 
		this.planetMap[14][19] = new MapTile("R"); 
		this.planetMap[14][20] = new MapTile("R"); 
		this.planetMap[14][21] = new MapTile("R"); 
		
		this.planetMap[6][23] = new MapTile("R");
		this.planetMap[7][23] = new MapTile("R");
		this.planetMap[7][23] = new MapTile("R");
		this.planetMap[8][24] = new MapTile("R");
		this.planetMap[8][25] = new MapTile("R");
		
		this.planetMap[24][10] = new MapTile("S");
		this.planetMap[24][11] = new MapTile("S");
		this.planetMap[24][12] = new MapTile("S");
		this.planetMap[25][10] = new MapTile("S");
		this.planetMap[25][11] = new MapTile("S");
		this.planetMap[25][12] = new MapTile("S");
		this.planetMap[25][13] = new MapTile("S");
		this.planetMap[26][10] = new MapTile("S");
		this.planetMap[26][11] = new MapTile("S");
		this.planetMap[26][12] = new MapTile("S");
		this.planetMap[26][13] = new MapTile("S");
	}
	
	public void loadSmallExampleTestPlanetMapTerrain(){
		// temporary use for creating planet terrain for testing
		
		this.mapHeight = 5;
		this.mapWidth = 5;
		this.planetMap = new MapTile[mapWidth][mapHeight];
		for(int j=0;j<mapHeight;j++){
			for(int i=0;i<mapWidth;i++){
				this.planetMap[i][j] = new MapTile();
			}
		}
		
		this.planetMap[2][2] = new MapTile("R"); 
		this.planetMap[3][2] = new MapTile("R"); 
		this.planetMap[1][4] = new MapTile("R"); 
	
		this.planetMap[3][3] = new MapTile("S");
	}
	
	// added ability to get map tiles
	public Map<Coord, MapTile> getAllTiles() {
		
		HashMap<Coord, MapTile> tiles = new HashMap<>();
		
		for(int j=0;j<mapHeight;j++){
			for(int i=0;i<mapWidth;i++){
				tiles.put(new Coord(i,j), this.planetMap[i][j]);
			}
		}
		
		return tiles;
	}
	
	
	// displays PlanetMap for debugging
	public void debugPrintMap(){

	int edgeSizeX = mapWidth;
	int edgeSizeY = mapHeight;
	
	Science science;
	Terrain terrain;
	MapTile tile;
	
	for(int k=0;k<edgeSizeX + 2;k++){System.out.print("--");}
	System.out.print("\n");
	for(int j= 0; j< edgeSizeY; j++){
		System.out.print("| ");
		for(int i= 0; i< edgeSizeX; i++){
			
			tile = planetMap[i][j];
			science = tile.getScience();
			terrain = tile.getTerrain();
			
			//check and print edge of map has first priority
			if(terrain.equals(Terrain.UNKNOWN)){
				System.out.print("::");
			}
			else if(terrain.equals(Terrain.NONE)){
				System.out.print("XX");
				
			// next most important - print terrain and/or science locations
				//terrain and science
			} else if(tile.getHasRover() || (startPosCoord.xpos == i && startPosCoord.ypos == j)) {
				System.out.print("[]");
			} else if(!terrain.equals(Terrain.SOIL) && !science.equals(Science.NONE)){
				// both terrain and science
				System.out.print(terrain.getTerString() + science.getSciString());
				//just terrain
			} else if(!terrain.equals(Terrain.SOIL)){
				System.out.print(terrain.getTerString() + " ");
				//just science
			} else if(!science.equals(Science.NONE)){
				System.out.print(" " + science.getSciString());
			} else {
				System.out.print("  ");
			}
		}	
		System.out.print(" |\n");		
	}
	for(int k=0;k<edgeSizeX +2;k++){
		System.out.print("--");
	}
	System.out.print("\n");
	}
	
}