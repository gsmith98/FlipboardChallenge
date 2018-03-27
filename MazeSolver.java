import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Stack;

import javax.json.*;

public class MazeSolver {

	public static void main(String[] args) {
		URL mazebase = createBaseURL();
		String seed = getStartRedirectedURLSeed(mazebase);

		String traveledPath = walk(mazebase, seed);

		if (checkPath(mazebase, seed, traveledPath)) {
			System.out.println("Found the exit for seed " + seed + "! The path traversed was:");
		} else {
			System.out.println("Lost forever in the maze with seed " + seed + " :(   The path traversed was:");
		}
		System.out.println(traveledPath);

	}

	/**
	 * This is just an initializer function that will call a URL constructor for
	 * the base Flipboard challenge URL and handle catch exceptions. It mostly
	 * exists to keep ugly try catch blocks out of main for something so small.
	 * 
	 * @return a URL object for the main Flipboard challenge page to be used for relative URL's
	 */
	public static URL createBaseURL() {
		try {
			return new URL("https://challenge.flipboard.com/");
		} catch (MalformedURLException mue) {
			mue.printStackTrace();
			System.exit(0);
			return null;
		}
	}

	/**
	 * Makes the start request for a random maze and returns the seed string for the given maze 
	 * (We can then start this maze by using grabJSONFromRelativeURL(mazebase, seed, <coords 0,0>) 
	 * since all mazes start at coordinates x = 0, y = 0
	 * 
	 * @param mazebase the base URL object for Flipboard's challenge
	 * @return the seed string for a random maze chosen by the 'start' URL
	 */
	public static String getStartRedirectedURLSeed(URL mazebase) {
		try {
			URLConnection con = new URL(mazebase, "start").openConnection();
			con.connect();
			con.getInputStream(); // this is the part that redirects the URL
			String urlstring = con.getURL().toString();
			return urlstring.substring(urlstring.indexOf("s=") + 2, urlstring.indexOf('&'));

		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(0);
			return null;
		}

	}

	/**
	 * Given a base URL object for the Flipboard challenge site and the
	 * parameters to be applied to it for the specific request, this function
	 * will return to you a JsonObject of the Flipboard server's response
	 * 
	 * @param mazebase the base URL object for Flipboard's challenge
	 * @param seed the seed for this maze
	 * @param coords a JsonObject containing the x and y coordinates to visit
	 * @return a JsonObject for the response from the maze server
	 */
	public static JsonObject grabJSONFromRelativeURL(URL mazebase, String seed, JsonObject coords) {
		try {
			String rel = "step?s=" + seed + "&x=" + coords.getInt("x") + "&y=" + coords.getInt("y");
			URL fullurl = new URL(mazebase, rel);
			JsonReader jsonreader = Json.createReader(fullurl.openStream());
			return jsonreader.readObject();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(0);
			return null;
		}

	}

	/**
	 * The central function that handles the logic of traversing the maze and keeping track.
	 * The guarantee that no path loops back on itself simplifies the maze to a tree, and
	 * the walking thereof would just be a Depth First Search save for the backtracking.
	 * The implementation is exactly like a DFS, but with an added auxiliary stack to keep
	 * track of our path so we can report the backtracking - without rerequesting steps.
	 * (The DFS part itself still 'jumps' after reaching a dead end.)
	 * 
	 * @param mazebase the base URL object for Flipboard's challenge
	 * @param seed the seed for this maze
	 * @return the string of letters representing the path traversed (including backtracks)
	 */
	private static String walk(URL mazebase, String seed) {
		//We maintain this HashSet just to avoid revisiting coordinates. The JsonObjects it stores are 'coords'. 
		//'Maintain' may be too strong a word, since add and contains are all we use and they are both O(1)
		HashSet<JsonObject> visitedCoords = new HashSet<JsonObject>();

		//This is the Stack that will act as the core data structure for the DFS through the maze. 
		//The JsonObjects it stores are 'coords'
		Stack<JsonObject> dfsStack = new Stack<JsonObject>();

		//We maintain this Stack to handle backtracking, the quirk that separates this from a regular DFS. 
		//As we travel, steps we take are pushed, and as we backtrack they are popped. An interesting result of
		//this is that this stack will be a representation of the optimal path through the maze when we're done. 
		//This Stack holds not 'coords', but the JsonObjects that come from the maze server's responses.
		Stack<JsonObject> truePathStack = new Stack<JsonObject>();

		String traveledPath = ""; //string of letters representing our traversal
		boolean done = false;
		dfsStack.push(Json.createObjectBuilder().add("x", 0).add("y", 0).build()); //starting coord 0,0 on stack
		while (!done) {
			JsonObject coords = dfsStack.pop();
			JsonObject current = grabJSONFromRelativeURL(mazebase, seed, coords);
			visitedCoords.add(coords);
			traveledPath += current.getString("letter");

			int pushed = 0;
			for (JsonObject adjxy : current.getJsonArray("adjacent").getValuesAs(JsonObject.class)) {
				if (!visitedCoords.contains(adjxy)) {
					dfsStack.push(adjxy);
					pushed++;
				}
			} //^this for loop pushes each adjacent coord JsonObj that we haven't visited to the dfsStack

			if (pushed == 0) { //if there weren't any viable adjacencies, we have to backtrack
				traveledPath = backtrack(truePathStack, traveledPath, dfsStack.peek());
			} else {
				truePathStack.add(current); //otherwise, add this step to the current true path
			}

			done = current.getBoolean("end");
		}

		return traveledPath;
	}

	/**
	 * This is a helper function for walk that encapsulates the approach to backtracking.
	 * The function uses the stack of our effective path thus far along with the coordinates
	 * that the DFS algorithm would 'jump' to and updates the traversal report to include
	 * walking back to the branch that DFS will pick up from.
	 * 
	 * @param truePathStack the effective path stack of 'response' JsonObjects from the maze server
	 * @param traveledPath the string of letters representing the path traversed thus far
	 * @param coordToReach the 'coord' JsonObject the brach next to which we want to backtrack to
	 * @return an updated traveledPath string of letters representing the path traversed thus far
	 */
	public static String backtrack(Stack<JsonObject> truePathStack, String traveledPath, JsonObject coordToReach) {
		boolean done = false;
		while (!done) {
			JsonObject current = truePathStack.pop(); //go back one step
			traveledPath += current.getString("letter"); //indicate the retracing of this step
			//we go until we get back to the point adjacent to where the DFS wants to pick up
			done = current.getJsonArray("adjacent").getValuesAs(JsonObject.class).contains(coordToReach);
			if (done) {
				//we just popped off the last step in the truepathstack that is still viable. Need to put it back.
				truePathStack.push(current); 
			}
		}
		return traveledPath;
	}

	/**
	 * A function to test a solution to a maze using the 'check' URL as described on the challenge page
	 * 
	 * @param mazebase the base URL object for Flipboard's challenge
	 * @param seed the seed for this maze
	 * @param traveledPath the path that 'walk' reported it used to find the end
	 * @return true if the response indicates success, false otherwise
	 */
	public static boolean checkPath(URL mazebase, String seed, String traveledPath) {
		try {
			String rel = "check?s=" + seed + "&guess=" + traveledPath;
			URL fullurl = new URL(mazebase, rel);
			JsonReader jsonreader = Json.createReader(fullurl.openStream());
			return jsonreader.readObject().getBoolean("success");
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(0);
			return false;
		}
	}

}
