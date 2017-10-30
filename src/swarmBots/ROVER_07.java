package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.json.simple.JSONArray;

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.PlanetMap;
import MapSupport.ScanMap;
import common.Rover;

import communicationInterface.Communication;

import enums.RoverDriveType;

import enums.RoverToolType;
import enums.Science;
import enums.Terrain;

import searchStrategy.AstarSearch;
import searchStrategy.SearchStrategy;
import searchStrategy.graph.Edge;
import searchStrategy.graph.Graph;
import searchStrategy.graph.Node;

/*
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

/**
 * @author original by: rkjc (ROVER_00)
 * 
 * @author heavility modified by: luisfisherjr
 * 
 * ROVER_07 is intended to be a the template for GreenCorp
 * Start by refactoring the class name to match your rovers name.
 * Then do a find and replace to change all the other instances of the 
 * name "ROVER_07" to match your rovers name.
 * 
 * The behavior of this robot will allow it to navigate the map properly
 * and collect resources using A* search
 * 
 * The behavior of this robot is determined by a state machine.
 *
 */

public class ROVER_07 extends Rover {
	
	// states if rover
	private enum State {
		UPDATING_PATH, MOVING, GATHERING, FINDING_RESOURCE, REACHED_TARGET, EXPLORING
	}
	
	// sub state of rover
	private enum Mode {
		SEARCH, REGATHER
	}
	
	private SearchStrategy searchStrategy = new AstarSearch(); // the search that we are using to find paths on graph
	private Set<String> drivableTerrain = new HashSet<String>(); // the terrain rover can drive on
	private Set<String> gatherableTerrain = new HashSet<String>(); // the terrain rover can gather on
	private Set<String> sensors = new HashSet<String>(); 
	
	private State roverState = State.UPDATING_PATH; // start state    UPDATING_PATH, EXPLORING, FINDING_RESOURCE, GATHERING, MOVING, REACHED_TARGET, PROTECTING
	private Mode roverMode = Mode.SEARCH; // start mode    EXPLORING, DEFENDING
	
	private long timeSinceLastMove = 10000L;
	private long timeSinceLastGather = 10000L;
	
	private long lagCushion = 50L; // helps performance, might need to alter
	
	private long moveCooldown = 10000L; // gets set in run() depending on drive type
	private long gatherCooldown = 3400L + lagCushion; // default to gather speed from RPC + 30
	private long sleepTime = 10000L;
	

	
	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_07 client;
    	// if a command line argument is present it is used
		// as the IP address for connection to RoverControlProcessor instead of localhost 
		
		if(!(args.length == 0)){
			client = new ROVER_07(args[0]);
		} else {
			client = new ROVER_07();
		}
		
		client.run();
	}

	public ROVER_07() {
		// constructor
		rovername = "ROVER_07"; // rover 1 is fasted used for testing
		System.out.println(rovername + " rover object constructed");
	}
	
	public ROVER_07(String serverAddress) {
		// constructor
		rovername = "ROVER_07"; // rover 1 is fasted used for testing
		System.out.println(rovername + " rover object constructed");
		SERVER_ADDRESS = serverAddress;
	}
	
	/**************************
	 * Communications Functions
	 ***************************/
	// get data from server and update field map
	
	
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
			
	        // **** Define the communication parameterspost message and open a connection to the 
			// SwarmCommunicationServer restful service through the Communication.java class interface
	        String url = "http://localhost:3742/api"; // <----------------------  this will have to be changed if multiple servers are needed
	        String corp_secret = "gz5YhL70a2"; // not currently used - for future implementation
	        communication = new Communication(url, rovername, corp_secret);
		       

			// sets up the connections for sending and receiving text from the RCP
			receiveFrom_RCP = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			sendTo_RCP = new PrintWriter(socket.getOutputStream(), true);
			
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
			 *  ### Retrieve static values from RoverControlProcessor (RCP) ###
			 *  These are called from outside the main Rover Process Loop
			 *  because they only need to be called once
			 */		
			
			// **** get equipment listing ****			
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");
			
			// separates the elements of list into more useful subgroups used below
			setEquipment(equipment);
		
			
			// **** Request START_LOC Location from SwarmServer **** this might be dropped as it should be (0, 0)
			// 
			startLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + startLocation);
			
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			targetLocation = getTargetLocation();
//			targetLocation = new Coord(20, 20); // target coord that is closer for testing
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
	        
			
			/**
			 *  ### Setting up variables to be used in the Rover control loop ###
			 *  add more as needed
			 */
	         
	    	
			Graph graph = null; // graph created to search on
	    	List<Edge> path = null; // path rover will take using searchStrategy and graph
	        Edge nextMove = null; // edge containing from current -> next node in graph coord
	        long startTime = 0; // used to check how long  outer while loop takes
	        
	        Map<Coord, MapTile> tiles = null; // data from global map
	        Set<Coord> tilesRoverCanWalkOn = null; // coords of all tiles rover can walk on
	        Set<Coord> tilesRoverCanGather = null; // coords of all tiles rover can gather
	        Set<Coord> tilesRoverCanProtect = null;
	        Set<Coord> tilesRoverCanAddInformationAbout = null; // coords of all tiles rover can gather
	        Set<Coord> unexploredTiles = null; // coords of all tiles with Terrain.UNKOWN
	        Set<Coord> teamMemberLocations = null;
	        
	        
	        Set<Coord> tilesToRegather = new HashSet<>(); // saves tiles that can gather for regathering
	        Set<Coord> tilesToRegatherRemaining = new HashSet<>(); // used in the find resource loop on regather mode 
	        
	        
	        Coord closestResourceCanGather = null; // closest resource rover can pick up
	        Coord closestTileToExplore = null; // closest tile that the rover can reveal some information about
	        Coord closestTeamMember = null; // closest tile that the rover can reveal some information about
	        Coord closestTileToRegather = null;
	        // stack that allows rover to target resources on way to a final destination
	        Stack<Coord> targetLocations = new Stack<>();
	        targetLocations.push(targetLocation);
	        
	        // no meaningful name used in minor calculations
	        int temp1 = 0;
	        int temp2 = 0;
			/**
			 *  ####  Rover controller process loop  ####
			 *  This is where all of the rover behavior code will go
			 *  
			 */
			while (true) {                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<		
				
				startTime = System.currentTimeMillis();
								
				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();
			
				if (targetLocations.size() > 0) {
					targetLocation = targetLocations.peek();
				}
				else {
					targetLocation = currentLoc;
				}

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current location
				scanMap = doScan();
				
				// modifies scanMap before sending it to communication server adding rover location and scan data
				addScannedDataToScanMap(scanMap);
				addThisRoverLocationToScanMap(scanMap);
					
				// ***** after doing a SCAN post scan data to the communication server ****
				// This sends map data to the Communications server which stores it as a global map.
	            // This allows other rover's to access a history of the terrain this rover has moved over.
				communication.postScanMapTiles(currentLoc, scanMap.getScanMap());
	     

				// ***** get GlobalMap from server *****
				// gets the GlobalMap in the form of a JSONArray from the server to and update its local map to it for pathing/searching
	            JSONArray getGlobalMapResponse = communication.getGlobalMap();

	            
	            // creates the GlobalMap from the JSONArray provided by the server and sets the local map 
	            globalMap = new PlanetMap(getGlobalMapResponse, currentLoc, targetLocation);
	            
	            // adds the locations of other rovers from the scanMap to the local map for pathing/searching
	            globalMap.addScanDataMissing(scanMap);
	            
	              
	            // testing...
	            // prints the globalMap to the Console output for debug purposes, unexplored tiles are marked as ::
	            // globalMap doesn't have tiles of Terrain.UNKOWN from server, it is reserved for unexplored tiles
//	            globalMap.debugPrintMap();
	            
	            // testing...
				// prints the scanMap to the Console output for debug purposes
//				scanMap.debugPrintMap();
	            
	            // done outside of loop for performance
	            tiles = globalMap.getAllTiles();
//	            tilesRoverCanWalkOn = tilesRoverCanWalkOn(tiles);
	            tilesRoverCanWalkOn = tilesRoverCanWalkOnOrAddInformation(tiles);
	            tilesRoverCanGather = tilesRoverCanGather(tiles);
	            tilesRoverCanProtect = tilesRoverCanProtect(tiles);
	            tilesRoverCanAddInformationAbout = tilesRoverCanAddInformationAbout(tiles);
	            unexploredTiles = unkownTiles(tiles);
	            teamMemberLocations = teamMemberLocations(tiles);     
	            
	            tilesToRegather.addAll(tilesRoverCanGather); // adds any new tiles that can be gathered to the set
	            
	            //TODO add set of all resources revield
	            // tilestThatHadResources = tilestThatHadResources(tiles); 
	            
	            // testing...
	            // prints out many variables to the Console to see if the are correct for debug purposes
	            System.out.println(communication.getAllRoverDetails());
//	            System.out.println(getGlobalMapResponse);
				System.out.println(rovername + " currentLoc at: " + currentLoc);
				System.out.println(rovername + " target location at: " + targetLocations.peek());
				System.out.println("team member locations: " + teamMemberLocations);
				System.out.println("resources " + rovername + " total map tiles:" + tiles.size());
				System.out.println("resources " + rovername + " tiles to explore: " + unexploredTiles.size());
				System.out.println("resources " + rovername + " tiles to scan: " + tilesRoverCanAddInformationAbout.size());
				System.out.println("resources " + rovername + " tiles can walk on: " + tilesRoverCanWalkOn.size());
				System.out.println("resources " + rovername + " tiles can gather: " + tilesRoverCanGather.size());
//				System.out.println("resources " + rovername + " tiles can protect: " + tilesRoverCanProtect.size());
				System.out.println("resources " + rovername + " regather set size: " + tilesToRegather.size());
				System.out.println("resources " + rovername + " targets remaining to regather: " + tilesToRegatherRemaining.size());
				
				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				// sleep until move cooldown is over
				Thread.sleep(moveCooldownRemaining());
				
				/** ####  Rover State Machine loop  ####
				 *  
				 *  This is where all of the rover change of behavior is determined
				 */
	            do {
	            	
	            	switch (roverState) {

	            	case EXPLORING: // exploring tiles that rover can add information about
	            		
	            		closestTileToExplore = closestTile(tilesRoverCanAddInformationAbout);
	            		
	            		if (closestTileToExplore != null) { // if you can add data to the map
	            			
	            			targetLocations.push(closestTileToExplore);
	            			
	            			System.out.println("new target to explore found entering state UPDATING_PATH...");
							roverState = State.UPDATING_PATH;
	            		}
	            		else {
	            			
	            	  			System.out.println("no target to explore changing mode to REGATHER...");
	            	  			System.out.println("populating tilesToRegatherRemaining...");
		            			System.out.println("entering state FINDING_RESOURCE...");
		            			
		            			tilesToRegatherRemaining.addAll(tilesToRegather);
		            			
		            			if (tilesToRegatherRemaining.size() == 0) {
		            				System.out.println("can't do anything sleeping...");
		            				Thread.sleep(sleepTime);
		            			}
				
		            			roverState = State.FINDING_RESOURCE;
		            			roverMode = Mode.REGATHER;
	            		}
	            		
	            		break;
	            	
					case FINDING_RESOURCE: // select a resource tile
						
						if (roverMode.equals(Mode.SEARCH)) {
							
							closestResourceCanGather = closestTile(tilesRoverCanGather);
							
							if (closestResourceCanGather != null) {
								
								targetLocations.push(closestResourceCanGather);
								
								System.out.println("new gather target found entering state UPDATING_PATH...");
								roverState = State.UPDATING_PATH;
							}
							else {
								
								System.out.println("no gather targets available entering state EXPLORING...");
								roverState = State.EXPLORING;
							}	
						}
						else if (roverMode.equals(Mode.REGATHER)) {
							
							closestTileToRegather = closestTile(tilesToRegatherRemaining);
							
		            		
		            		if (closestTileToRegather != null) {
		            			
		            			targetLocations.push(closestTileToRegather);
		            			
		            			System.out.println("new regather target found entering state UPDATING_PATH...");
			            		roverState = State.UPDATING_PATH;
		            		}
		            		else {
		            			
		            			System.out.println("no regather targets left changing mode to SEARCH...");
								System.out.println("entering state FINDING_RESOURCE...");
						
								roverState = State.FINDING_RESOURCE;
								roverMode = Mode.SEARCH;
		            		}
						}
					
						break;
						
					case UPDATING_PATH: // refreshes path if obstacle is found
						
						targetLocation = targetLocations.peek(); // get target
	
						path = null;
						
						if (tilesRoverCanWalkOn.contains(targetLocation)) { // tries to pake path is possible
							
							graph = new  Graph(globalMap.getWidth(), globalMap.getHeight(), tilesRoverCanWalkOn);
							path = findPath(graph, currentLoc, targetLocation);
						}
						
						if (path == null) { // what to do if no path exists
							
				            tilesRoverCanGather.remove(targetLocation);
				            tilesRoverCanAddInformationAbout.remove(targetLocation);
				            tilesRoverCanWalkOn.remove(targetLocation);
				            
				            targetLocations.pop(); // target unreachable removing from stack
							
							if (!targetLocations.isEmpty()) {
								System.out.println("target unreachable continuing onto original target entering state UPDATING_PATH...");
								roverState = State.UPDATING_PATH;
							}
							else {
								
								System.out.println("target unreachable entering state FINDING_RESOURCE...");
								roverState = State.FINDING_RESOURCE;
							}
						}
						else {
							
							System.out.println("path found entering state MOVING...");
							roverState = State.MOVING;
						}
						
						break;
						
					case REACHED_TARGET: // what to do once target is reached
						
						targetLocations.pop();
						
						if (roverMode.equals(Mode.SEARCH)) {
							
							if (tilesRoverCanGather.contains(currentLoc)) {
								
								System.out.println("location has attainable resources entering state GATHERING...");
								roverState = State.GATHERING;
							}
							else if (!targetLocations.isEmpty()) {
								System.out.println("no resource to gather continuing onto original target entering state UPDATING_PATH...");
								roverState = State.UPDATING_PATH;
							}
							else {
								
								System.out.println("location has no attainable resources entering state FINDING_RESOURCE...");
								roverState = State.FINDING_RESOURCE;
							}
						}
						else if (roverMode.equals(Mode.REGATHER)) {
							
							System.out.println("location for regather reached entering state GATHERING...");
							roverState = State.GATHERING;
						}
	
						break;
		
					case GATHERING: // gathering steps
						
						Thread.sleep(gatherCooldownRemaining()); // this keeps the rover on resource so other rovers do not pick it up before it is ready
							
						Thread.sleep(lagCushion * 4); // sleep regardless if cooldown so move req doesnt mess with gather req
						
						if(roverMode.equals(Mode.SEARCH)) {
							
							gatherScience(currentLoc); // gather tile command sent to RPC and removed, this will also remove from server
							
							tilesRoverCanGather.remove(currentLoc); // this is just to remove tile from list of tiles to gather	
							
							if (!targetLocations.isEmpty()) { // there is still a target
								
								System.out.println("gathering complete continuing onto original target entering state UPDATING_PATH...");
								roverState = State.UPDATING_PATH;
							} else {
								
								System.out.println("gathering complete entering state FINDING_RESOURCE...");
								roverState = State.FINDING_RESOURCE;
							}
						}
						else if(roverMode.equals(Mode.REGATHER)) {
							
							sendTo_RCP.println("GATHER"); // dont send anything to the green server just RCP
							
							tilesToRegatherRemaining.remove(currentLoc);  // this is just to remove tile from list of tiles to regather	
							
							if (!tilesToRegatherRemaining.isEmpty()) { // there is still a target
								
								System.out.println("gathering complete continuing onto next regather target entering state FINDING_RESOURCE...");
								roverState = State.FINDING_RESOURCE;
							}
							else {
								System.out.println("no regather targets remaining entering state EXPLORING...");
								roverState = State.EXPLORING;
							}
						}		
						
						resetGatherCooldown(); // reset gather cooldown
						
						break;
						
					case MOVING: // try and move to next target
						
						targetLocation = targetLocations.peek();
						
						closestResourceCanGather = closestTile(tilesRoverCanGather);
						
						if (closestResourceCanGather != null) { // checks if there is a resource to get on path to target
							
							 temp1 = manhattenDistance(closestResourceCanGather, currentLoc);
						     temp2 = manhattenDistance(targetLocation, currentLoc);
							
							if ((temp1 < temp2) && (targetLocations.size() < 2) && (temp1 < 10)) {
						
								targetLocations.push(closestResourceCanGather);
								
								System.out.println("resource near path to target changing targets entering state UPDATING_PATH...");
								roverState = State.UPDATING_PATH;
								
								break;
							}
						}
				
						nextMove = getNexMove(path); // get next move uses current location and a path
						
						if (currentLoc.equals(targetLocation)) { // I have no next move have I reached target
							
							System.out.println("reached target entering state REACHED_TARGET...");
							roverState = State.REACHED_TARGET;
							
						}
						else if (canMoveTo(nextMove)) { // check is next tile has a known obstacle
								
							move(nextMove); // attempt to move to tile
							
							resetMoveCooldown(); // resets move cooldown after move happens
							
							System.out.println("moved to next MapTile entering state MOVING...");
							roverState = State.MOVING;
						}
						else {
							
							tilesRoverCanWalkOn.remove((Coord)nextMove.getTo().getData()); // removes coord to recalculate new path
							
							System.out.println("path blocked entering state UPDATING_PATH...");
							roverState = State.UPDATING_PATH;
						}
						
						break;
					}
	            	
	            } while (roverState != State.MOVING); // if rover has reached a target to defend exit loop
	            
	            /** #### end of Rover State Machine loop  #### */
	            
	            
	            timeRemaining = getTimeRemaining();
				System.out.println(rovername + " ------------ END PROCESS CONTROLL LOOP -----TIME: " + (System.currentTimeMillis() - startTime));

				
			}  // ***** END of Rover control While(true) loop *****
					
		// This catch block hopefully closes the open socket connection to the server
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println(rovername + " problem closing socket");
	            }
	        }
	    }

	} // END of Rover run thread
	

	// ####################### Additional Support Methods #############################
	// add new methods and functions here


	/**
 	 * Method that sends the move command to RPC to move to next position on path.This
 	 * method simplifies calls to moveEast(), moveWest(), moveSouth(), moveNorth() by taking a
 	 * move in the form of the edge (current -> next) and selecting the correct move.
 	 *
 	 * @param Edge edge : the Edge (current -> next)
	 */
	private void move(Edge edge) {

		Coord current = (Coord)edge.getFrom().getData();
		Coord next = (Coord)edge.getTo().getData();
		
		if (current.xpos < next.xpos) moveEast();
		if (current.xpos > next.xpos) moveWest();
		if (current.ypos < next.ypos) moveSouth();
		if (current.ypos > next.ypos) moveNorth();
	}
	
	
	/**
 	 * Method that returns a true if the target in the edge (origin -> target) can be reached by this rover.
  	 * 
  	 * @param Edge edge : the Edge (current -> next)
	 * 
	 * @return boolean : is the target is a valid destination
	 */
	private boolean canMoveTo(Edge nextMove) {
		
		//XXX this is for testing, basically lets you know how you arrived at this null point
		if (nextMove == null) {
			System.out.println("You messed up passing to move state without finding path FIX YOUR CODE");
			System.exit(-1);
		}
		
		Coord nextCoord = (Coord)nextMove.getTo().getData();
		MapTile nextMoveGobalTile = globalMap.getTile(nextCoord);
		
		boolean nextHasRover = nextMoveGobalTile.getHasRover();
		String nextTerrain = nextMoveGobalTile.getTerrain().getTerString();
		
		return (!nextHasRover && drivableTerrain.contains(nextTerrain));
	}
	
	
	/**
 	 * Method that returns a the next move (Edge) in the path (List<Edge>) based 
 	 * on the variable currentLoc.
 	 * 
  	 * @param List<Edge> path : the path to target
	 * 
	 * @return Edge : the next move
	 */
	private Edge getNexMove(List<Edge> path) {
		
		for (Edge edge: path) {
			
			if(((Coord)edge.getFrom().getData()).equals(currentLoc)) {
				return edge;
			}
		}
		return null;
	}
	
	
	/**
 	 * Method that returns a long representing the milliseconds until the RPC gather 
 	 * cooldown is finished for this rover. If the cooldown is complete returns 0.
	 * 
	 * @return Long : the time until RCP will accept another gather
	 */
	private long gatherCooldownRemaining() {
	
		long remaindingTime =  gatherCooldown - (System.currentTimeMillis() - timeSinceLastGather);
		
		if (remaindingTime < 0) {
			
			remaindingTime = 0;
		}
		
		return remaindingTime;
	}
	
	
	/**
	 * Method that modifies the variable timeSinceLastGather by reseting it to the System.currentTimeMillis().
	 * this method is a simple wrapper that adds readability to the code in state machine.
	 */
	private void resetGatherCooldown() {
		
		timeSinceLastGather = System.currentTimeMillis();
	}
	
	
	/**
 	 * Method that returns a long representing the milliseconds until the RPC move 
 	 * cooldown is finished for this rover. If the cooldown is complete returns 0.
	 * 
	 * @return Long representing the time until RCP will accept another move
	 */
	private long moveCooldownRemaining() {

		long remaindingTime =  moveCooldown - (System.currentTimeMillis() - timeSinceLastMove);
		
		if (remaindingTime < 0) {
			
			remaindingTime = 0;
		}
		
		return remaindingTime;
	}
	
	
	/**
	 * Method that modifies the variable timeSinceLastMove by reseting it to the System.currentTimeMillis().
	 * this method is a simple wrapper that adds readability to the code in state machine.
	 */
	private void resetMoveCooldown() {
		
		timeSinceLastMove = System.currentTimeMillis();
	}

	
	/**
 	 * Method that returns list of edges or "path" from start to target (start -> target)
 	 * If start or target are not inside of the graph returns null.
 	 * If there is no path from start to target returns null.
 	 * 
  	 * @param Graph graph : graph built with the constructor Graph(int , int, Set<Coord>) 
  	 * @param Coord start : start coordinate in the graph
  	 * @param Coord target : target coordinate in the graph
	 * 
	 * @return List<Edge> representing the path, start -> target.
	 */
	private List<Edge> findPath(Graph graph , Coord start, Coord target) {
		
		@SuppressWarnings("rawtypes")
		Node startNode = graph.getNode(new Node<>(start));
		
		@SuppressWarnings("rawtypes")
		Node targetNode = graph.getNode(new Node<>(target));
		
		if (graph.getNode(startNode) == null ||
				graph.getNode(targetNode) == null) return null;
		
		return searchStrategy.search(graph, startNode, targetNode);	
	}
	
	
	/**
	 * Method that modifies the variables sensors, drivableTerrain, and gatherableTerrain. This method
	 * adds the correct elements to those variables from a list of parts.
	 * 
 	 * @param List<String> parts : equipment list received from RCP
	 */
	private void setEquipment(List<String> parts) {

		for(String part: parts) {
			setDrivableTerrain(RoverDriveType.getEnum(part));
			setGatherableTerrain(RoverToolType.getEnum(part));
			setSensors(RoverToolType.getEnum(part));
		}	
	}
	
	
	/**
	 * Helper method used in the method setEquipment(List<String>). This method modifies
	 * the drivableTerrain class variable by adding elements to it and also sets the correct
	 * moveCooldown for the drive.
	 * 
	 * @param RoverDriveType drive : drive type of the rover
	 */
	private void setDrivableTerrain(RoverDriveType drive) {
		
		switch (drive) {
		
		case WALKER:
			drivableTerrain.add(Terrain.UNKNOWN.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			
			drivableTerrain.add(Terrain.ROCK.getTerString());
			moveCooldown = 1200L + lagCushion;
			break;
			
		case TREADS:
			drivableTerrain.add(Terrain.UNKNOWN.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			
			drivableTerrain.add(Terrain.SAND.getTerString());
			moveCooldown = 900L + lagCushion;
			break;
			
		case WHEELS:
			drivableTerrain.add(Terrain.UNKNOWN.getTerString()); 
			drivableTerrain.add(Terrain.SOIL.getTerString());
			drivableTerrain.add(Terrain.GRAVEL.getTerString());
			moveCooldown = 400L + lagCushion;
			break;
			
		default:
			break;
		}
	}
	
	
	/**
	 * Helper method used in the method setEquipment(List<String>). This method modifies
	 * the gatherableTerrain class variable by adding elements to it.
	 * 
	 * @param RoverDriveType tool : tool type of the rover
	 */
	private void setGatherableTerrain(RoverToolType tool) {
		// all types can traverse these
		
		switch (tool) {
		
		case EXCAVATOR:
			gatherableTerrain.add(Terrain.SAND.getTerString());
			gatherableTerrain.add(Terrain.SOIL.getTerString());
			break;
			
		case DRILL:
			gatherableTerrain.add(Terrain.ROCK.getTerString());
			gatherableTerrain.add(Terrain.GRAVEL.getTerString());
			break;
			
		default:
			break;
		}
	}
	
	
	/**
	 * Helper method used in the method setEquipment(List<String>). This method modifies
	 * the sensor class variable by adding elements to it.
	 * 
	 * @param RoverDriveType tool : tool type of the rover
	 */
	private void setSensors(RoverToolType tool) {
		// all types can traverse these
		
		switch (tool) {
		
		case CHEMICAL_SENSOR:
			sensors.add("CHEMICAL_SENSOR");
			break;
			
		case RADAR_SENSOR:
			sensors.add("RADAR_SENSOR");
			break;
			
		case RADIATION_SENSOR:
			sensors.add("RADIATION_SENSOR");
			break;
			
		case SPECTRAL_SENSOR:
			sensors.add("SPECTRAL_SENSOR");
			break;
		default:
			break;
		}
	}
	
	/**
 	 * Method that returns the closest Coord to the rover from a Set of coordinates.
	 * 
	 * @param Set<Coord> coordinates :  coordinates to compare to currentLoc
	 * 
	 * @return Coord : the closest coord by manhattan distance
	 */
	private Coord closestTile(Set<Coord> coordinates) {
		
		if (coordinates == null) return null;
		if (coordinates.size() == 0) return null;
		
		Coord closestCoord = null;

		int distance = 0;
		int closestDistance = Integer.MAX_VALUE;
		
		for(Coord coord: coordinates) {
			
			distance = manhattenDistance(currentLoc, coord);
			
			if (distance < closestDistance) {
				
				closestDistance = distance;
				closestCoord = coord;
			}	
		}
		
		return closestCoord;
	}
	
	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that this rover can gather (tiles rover can walk on and has tools to gather that terrain type).
	 * 
 	 * @param Map<Coord, MapTile> coordMaping : the information from globalMap passed as a parameter for performance
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesRoverCanGather(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> canWalkOnWithResources = tilesRoverCanWalkOn(coordMaping);
		Set<Coord> canGatherOn = new HashSet<Coord>();
		
		MapTile tile = null;
		String terrain = "";
		
		for(Coord coord: canWalkOnWithResources) {
		
			
			tile = globalMap.getTile(coord);
			
			if (tile.getScience().equals(Science.NONE)) continue;
			
			terrain = tile.getTerrain().getTerString();
			
			if(gatherableTerrain.contains(terrain)) {
				
				canGatherOn.add(coord);
			}
		}
		
		return canGatherOn;
	}
	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that this rover can't gather but can reach (tiles rover can walk on and does not have tools to gather on that terrain type).
	 * 
 	 * @param Map<Coord, MapTile> coordMaping : the information from globalMap passed as a parameter for performance
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesRoverCanProtect(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> canWalkOnWithResources = tilesRoverCanWalkOn(coordMaping);
		Set<Coord> canProtectOn = new HashSet<Coord>();
		
		MapTile tile = null;
		String terrain = "";
		
		for(Coord coord: canWalkOnWithResources) {
		
			
			tile = globalMap.getTile(coord);
			
			if (tile.getScience().equals(Science.NONE)) continue;
			
			terrain = tile.getTerrain().getTerString();
			
			if(!gatherableTerrain.contains(terrain)) {
				
				canProtectOn.add(coord);
			}
		}
		
		return canProtectOn;
	}
	
	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that have Terrain of types that this rover can move to that do not have rovers.
	 * 
 	 * @param Map<Coord, MapTile> coordMaping : the information from globalMap passed as a parameter for performance
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesRoverCanWalkOn(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> canWalkOn = new HashSet<>();
		
		Coord coord;
		MapTile tile;
		String terrain;
		
		for (Map.Entry<Coord, MapTile> entry : coordMaping.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			terrain = tile.getTerrain().getTerString();
			
			if(drivableTerrain.contains(terrain) && !tile.getHasRover()) {
				
				canWalkOn.add(coord);
			}
		}
		
		return canWalkOn;
	}
	
	private Set<Coord> tilesRoverCanWalkOnOrAddInformation(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> canWalkOnAndAddInformationTo = tilesRoverCanWalkOn(coordMaping);
		canWalkOnAndAddInformationTo.addAll(tilesRoverCanAddInformationAbout(coordMaping));
		
		return canWalkOnAndAddInformationTo;
	}
	
	
	/**
 	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that have Terrain of type UNKOWN.
	 * 
 	 * @param Map<Coord, MapTile> coordMaping : the information from globalMap passed as a parameter for performance
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> unkownTiles(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> uknownTiles = new HashSet<>();
		
		Coord coord;
		MapTile tile;
		String terrain;
		
		for (Map.Entry<Coord, MapTile> entry : coordMaping.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			terrain = tile.getTerrain().getTerString();
			
			if(terrain.equals(Terrain.UNKNOWN.getTerString())) {
				
				uknownTiles.add(coord);
			}
		}
		
		return uknownTiles;
	}
	
	//TODO add set of all resources revield
	private Set<Coord> tilestThatHadResources(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> uknownTiles = new HashSet<>();
		
		Coord coord;
		MapTile tile;
		String terrain;
		
		for (Map.Entry<Coord, MapTile> entry : coordMaping.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			
			//TODO change
			terrain = tile.getTerrain().getTerString();
			
			
			if(terrain.equals(Terrain.UNKNOWN.getTerString())) {
				
				uknownTiles.add(coord);
			}
		}
		
		return uknownTiles;
	}
	
	
	/**
	 * Method that returns a subset of Coord from MapTiles in globalMap. This subset consists of 
	 * Coord of tiles that have not been scanned by a scanner that this rover possess. If the rover
	 * does not poses any scanners it will return all tiles with type terrain.UNKOWN these can be
	 * explored by rovers without scanners
	 * 
	 * @param Map<Coord, MapTile> coordMaping : the information from globalMap passed as a parameter for performance
	 * 
	 * @return Set<Coord> subset of globalMap.getAllTiles().keySet()
	 */
	private Set<Coord> tilesRoverCanAddInformationAbout(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> tilesRoverCanAddInformationAbout = new HashSet<>();
		
		Set<String> scannedBy;
		
		Coord coord;
		MapTile tile;
		String terrain;
		
		if(sensors.size() > 0) { // if rover has any sensor do this for loop
			
			for (Map.Entry<Coord, MapTile> entry : coordMaping.entrySet()) {
				
				coord = entry.getKey();
				tile = entry.getValue();
				
				scannedBy = tile.getScannedBySensors();
				
				for (String sensor: sensors) {
					
					if(!scannedBy.contains(sensor)) {
						
						tilesRoverCanAddInformationAbout.add(coord);
						break;
					}
				}
			}
		}
		else { // if rover has no sensor do this for loop
			
			for (Map.Entry<Coord, MapTile> entry : coordMaping.entrySet()) {
				
				coord = entry.getKey();
				tile = entry.getValue();
				terrain = tile.getTerrain().getTerString();
				
				if(terrain.equals(Terrain.UNKNOWN.getTerString())) {
					
					tilesRoverCanAddInformationAbout.add(coord);
				}
			}
		}
	
		return tilesRoverCanAddInformationAbout;
	}

	
	/**
	 * Method that modifies the MapTiles in a ScanMap by altering the binary
	 * String that represents the sensors that have scanned the MapTiles. The the altered
	 * binary String will indicate that the sensors the rover has scanned the MapTile.
	 * 
	 * @param ScanMap scanMap : ScanMap to modify
	 */
	private void addScannedDataToScanMap(ScanMap scanMap) {
		
		MapTile[][] mapTiles = scanMap.getScanMap();
		
		for(int j = 0; j < mapTiles.length; j++) {
			for(int i = 0; i < mapTiles[0].length; i++) {

				mapTiles[i][j].setScannedBySensor(sensorsToString());
			}
		}	
	}
	
	
	/**
	 * Method that modifies the MapTiles in a ScanMap by adding rover position so other rovers know
	 * where this rover is.
	 * 
	 * @param ScanMap scanMap : ScanMap to modify
	 */
	private void addThisRoverLocationToScanMap(ScanMap scanMap) {
		
		MapTile[][] mapTiles = scanMap.getScanMap();
		
		mapTiles[3][3].setRoverName(rovername);
	}
	
	
	/**
	 * Helper method used in addScannedDataToScanMap(ScanMap) that returns
	 * a binary representation of what sensors this rover has.
	 * 
	 * @return Returns a String of 0's and 1's length 4
	 */
	private String sensorsToString() {
		
		char[] sensorArray = {'0','0','0','0'};
		
		if (sensors.contains("CHEMICAL_SENSOR")) sensorArray[0] = '1';
		if (sensors.contains("RADAR_SENSOR")) sensorArray[1] = '1';
		if (sensors.contains("RADIATION_SENSOR")) sensorArray[2] = '1';
		if (sensors.contains("SPECTRAL_SENSOR")) sensorArray[3] = '1';
		
		return new String(sensorArray);
	}
	
	
	/**
	 * Method that returns an estimation of the distance needed to travel
	 * to reach a coordiate.
	 * 
	 * @param Coord first : the first coord for comparison
	 * @param Coord second : the second coord for comparison
	 * 
	 * @return int : minimum distance between targets
	 */
	private int manhattenDistance(Coord first, Coord second) {
		return 	Math.abs(first.xpos - second.xpos) + Math.abs(first.ypos - second.ypos);
	}
	
	/**
	 * Method that returns the coordinates of the other rovers on the team
	 * 
	 * @param Map<Coord, MapTile> coordMaping : the information from globalMap passed as a parameter for performance
	 * 
	 * @return Set<Coord> : Coords of team member
	 */
	private Set<Coord> teamMemberLocations(Map<Coord, MapTile> coordMaping) {
		
		Set<Coord> roverCoords = new HashSet<>();
		
		Coord coord;
		MapTile tile;
		String roverName;
		
		for (Map.Entry<Coord, MapTile> entry : coordMaping.entrySet()) {
			
			coord = entry.getKey();
			tile = entry.getValue();
			roverName = tile.getRoverName();
			
			if(roverName.equals(this.rovername) || roverName.equals("")) {
				continue;
			}
			
			roverCoords.add(coord);
		}
		
		return roverCoords;
	}
	
}