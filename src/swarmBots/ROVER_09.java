package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.ScanMap;
import common.Rover;
import communicationInterface.Communication;
import enums.Terrain;

/*
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

/**
 * 
 * @author rkjc
 * 
 * ROVER_09 is intended to be a basic template to start building your rover on
 * Start by refactoring the class name to match your rovers name.
 * Then do a find and replace to change all the other instances of the 
 * name "ROVER_09" to match your rovers name.
 * 
 * The behavior of this robot is a simple travel till it bumps into something,
 * sidestep for a short distance, and reverse direction,
 * repeat.
 * 
 * This is a terrible behavior algorithm and should be immediately changed.
 *
 */

public class ROVER_09 extends Rover {

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_09 client;
    	// if a command line argument is present it is used
		// as the IP address for connection to RoverControlProcessor instead of localhost 
		
		if(!(args.length == 0)){
			client = new ROVER_09(args[0]);
		} else {
			client = new ROVER_09();
		}
		
		client.run();
	}

	public ROVER_09() {
		// constructor
		System.out.println("ROVER_09 rover object constructed");
		rovername = "ROVER_09";
	}
	
	public ROVER_09(String serverAddress) {
		// constructor
		System.out.println("ROVER_09 rover object constructed");
		rovername = "ROVER_09";
		SERVER_ADDRESS = serverAddress;
	}

	/**
	 * 
	 * The Rover Main instantiates and runs the rover as a runnable thread
	 * 
	 */
	private void run() throws IOException, InterruptedException {
		// Make a socket for connection to the RoverControlProcessor
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			// sets up the connections for sending and receiving text from the RCP
			receiveFrom_RCP = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			sendTo_RCP = new PrintWriter(socket.getOutputStream(), true);
			
			// Need to allow time for the connection to the server to be established
			sleepTime = 300;
			
			/*
			 * After the rover has requested a connection from the RCP
			 * this loop waits for a response. The first thing the RCP requests is the rover's name
			 * once that has been provided, the connection has been established and the program continues 
			 */
			while (true) {
				String line = receiveFrom_RCP.readLine();
				if (line.startsWith("SUBMITNAME")) {
					//This sets the name of this instance of a swarmBot for identifying the thread to the server
					sendTo_RCP.println(rovername); 
					break;
				}
			}
	
	
			
			/**
			 *  ### Setting up variables to be used in the Rover control loop ###
			 *  add more as needed
			 */
			int stepCount = 0;	
			String line = "";	
			boolean goingSouth = false;
			boolean goingNorth = false;
			
			boolean goingWest = false;
			boolean stuck = false; // just means it did not change locations between requests,
									// could be velocity limit or obstruction etc.
			boolean blocked = false;
	
			// might or might not have a use for this
			String[] cardinals = new String[4];
			cardinals[0] = "N";
			cardinals[1] = "E";
			cardinals[2] = "S";
			cardinals[3] = "W";	
			String currentDir = cardinals[0];		
			

			/**
			 *  ### Retrieve static values from RoverControlProcessor (RCP) ###
			 *  These are called from outside the main Rover Process Loop
			 *  because they only need to be called once
			 */		
			
			// **** get equipment listing ****			
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");
			
			
			// **** Request START_LOC Location from SwarmServer **** this might be dropped as it should be (0, 0)
			startLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + startLocation);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			targetLocation = getTargetLocation();
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
			
			
	        // **** Define the communication parameters and open a connection to the 
			// SwarmCommunicationServer restful service through the Communication.java class interface
	        String url = "http://localhost:3742/api"; // <----------------------  this will have to be changed if multiple servers are needed
	        String corp_secret = "gz5YhL70a2"; // not currently used - for future implementation
	
	        Communication com = new Communication(url, rovername, corp_secret);
	

			/**
			 *  ####  Rover controller process loop  ####
			 *  This is where all of the rover behavior code will go
			 *  
			 */
			while (true) {                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
		
				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();
				System.out.println(rovername + " currentLoc at start: " + currentLoc);
				
				// after getting location set previous equal current to be able
				// to check for stuckness and blocked later
				previousLoc = currentLoc;		
				
				

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				scanMap = doScan(); 
				// prints the scanMap to the Console output for debug purposes
				scanMap.debugPrintMap();
				
				
				
				// ***** after doing a SCAN post scan data to the communication server ****
				// This sends map data to the Communications server which stores it as a global map.
	            // This allows other rover's to access a history of the terrain this rover has moved over.

	            System.out.println("do com.postScanMapTiles(currentLoc, scanMapTiles)");
	            System.out.println("post message: " + com.postScanMapTiles(currentLoc, scanMap.getScanMap()));
	            System.out.println("done com.postScanMapTiles(currentLoc, scanMapTiles)");

				
							
				// ***** get TIMER time remaining *****
				timeRemaining = getTimeRemaining();
				
	
				
				// ***** MOVING *****
				
				if (blocked) {
					if(stepCount > 0){
						if(southBlocked() == true && westBlocked() == false){
							moveWest();
							stepCount -=1;
						}
						else if(southBlocked() == true && westBlocked() == true){
							moveEast();
							stepCount -=1;
						}
						else if(southBlocked() == true && eastBlocked() == true){
							moveWest();
							stepCount -=1;
						}
						else{
							moveSouth();

							stepCount -=1;
						}
					}
					else {
						blocked = false;
						//reverses direction after being blocked and side stepping
						goingWest = !goingWest;
					}

				} else {

					// pull the MapTile array out of the ScanMap object
					MapTile[][] scanMapTiles = scanMap.getScanMap();
					int centerIndex = (scanMap.getEdgeSize() - 1)/2;



					if (goingWest) {
						// check scanMap to see if path is blocked to the West
						// (scanMap may be old data by now)
						if (scanMapTiles[centerIndex -1][centerIndex].getHasRover() 
								|| scanMapTiles[centerIndex -1][centerIndex].getTerrain() == Terrain.SAND
								|| scanMapTiles[centerIndex -1][centerIndex].getTerrain() == Terrain.NONE) {
							blocked = true;
							stepCount = 5;  //side stepping
						} else {
							// request to server to move
							moveWest();
						}

					} else {
						// check scanMap to see if path is blocked to the East
						// (scanMap may be old data by now)

						if (scanMapTiles[centerIndex +1][centerIndex].getHasRover() 
								|| scanMapTiles[centerIndex +1][centerIndex].getTerrain() == Terrain.SAND
								|| scanMapTiles[centerIndex +1][centerIndex].getTerrain() == Terrain.NONE) {
							System.out.println("########EAST BLOCKED########");
							blocked = true;
							stepCount = 5; 
						} else {
							
							moveEast();			
						}					
					
					}
					}// End of movement 
	
				// another call for current location
				currentLoc = getCurrentLocation();

	
				// test for stuckness
				stuck = currentLoc.equals(previousLoc);	
				
				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				Thread.sleep(sleepTime);
				
				System.out.println("ROVER_09 ------------ end process control loop --------------"); 
			}  // ***** END of Rover control While(true) loop *****
		
			
			
		// This catch block hopefully closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println("ROVER_09 problem closing socket");
	            }
	        }
	    }

	} // END of Rover run thread
	
	// ####################### Additional Support Methods #############################
	
	
	//Check North Block
			public boolean northBlocked(){
				// pull the MapTile array out of the ScanMap object
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				int centerIndex = (scanMap.getEdgeSize() - 1)/2;
				// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1
				if (scanMapTiles[centerIndex-1][centerIndex].getHasRover() 
						|| scanMapTiles[centerIndex-1][centerIndex].getTerrain() == Terrain.SAND
						|| scanMapTiles[centerIndex-1][centerIndex].getTerrain() == Terrain.NONE) {
					System.out.println("########NORTH BLOCKED########");
					return true;
				} else {

					return false;
				}
			}
			
	
		//Check East Block
		public boolean eastBlocked(){
			// pull the MapTile array out of the ScanMap object
			MapTile[][] scanMapTiles = scanMap.getScanMap();
			int centerIndex = (scanMap.getEdgeSize() - 1)/2;
			// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1
			if (scanMapTiles[centerIndex+1][centerIndex].getHasRover() 
					|| scanMapTiles[centerIndex+1][centerIndex].getTerrain() == Terrain.SAND
					|| scanMapTiles[centerIndex+1][centerIndex].getTerrain() == Terrain.NONE) {
				System.out.println("########EAST BLOCKED########");
				return true;
			} else {

				return false;
			}
		}
		
		//Check South Block
				public boolean southBlocked(){
					// pull the MapTile array out of the ScanMap object
					MapTile[][] scanMapTiles = scanMap.getScanMap();
					int centerIndex = (scanMap.getEdgeSize() - 1)/2;
					// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1
					if (scanMapTiles[centerIndex][centerIndex+1].getHasRover() 
							|| scanMapTiles[centerIndex][centerIndex+1].getTerrain() == Terrain.SAND
							|| scanMapTiles[centerIndex][centerIndex+1].getTerrain() == Terrain.NONE) {
						System.out.println("########SOUTH BLOCKED########");
						return true;
					} else {

						return false;
					}
				}

		//Check West Block
		public boolean westBlocked(){
			// pull the MapTile array out of the ScanMap object
			MapTile[][] scanMapTiles = scanMap.getScanMap();
			int centerIndex = (scanMap.getEdgeSize() - 1)/2;
			// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1
			if (scanMapTiles[centerIndex-1][centerIndex].getHasRover() 
					|| scanMapTiles[centerIndex-1][centerIndex].getTerrain() == Terrain.SAND
					|| scanMapTiles[centerIndex-1][centerIndex].getTerrain() == Terrain.NONE) {
				System.out.println("########WEST BLOCKED########");
				return true;
			} else {

				return false;
			}
		}


		

	
	// add new methods and functions here


}