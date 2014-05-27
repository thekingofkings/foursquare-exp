package exp;

import java.util.HashMap;
import java.util.LinkedList;



public class User {
	static HashMap<Integer, User> allUserSet = new HashMap<Integer, User>();
	static HashMap<Integer, User> frequentUserSet = new HashMap<Integer, User>();
	static String dirPath = "../../dataset/foursquare/sorteddata";
	
	int userID;
	LinkedList<Record> records;
	
	/*
	 * Construct the allUserSet (static field) in User class
	 */
	User( Record r ) {
		userID = r.userID;
		records = new LinkedList<Record>();
		records.add(r);
		if ( ! allUserSet.containsKey(r.userID))
			allUserSet.put(r.userID, this);
	}

}
