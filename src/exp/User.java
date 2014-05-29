package exp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;



public class User {
	static HashMap<Integer, User> allUserSet = new HashMap<Integer, User>();
	static String userDir = "../../dataset/foursquare/sorted/";
	static double para_c = 1.5;
	
	
	int userID;
	LinkedList<Record> records;
	HashSet<Integer> friends;
	HashSet<Long> locs;
	
	/*
	 * Construct the allUserSet (static field) in User class
	 */
	User( Record r ) {
		userID = r.userID;
		records = new LinkedList<Record>();
		friends = new HashSet<Integer>();
		locs = new HashSet<Long>();
		
		records.add(r);
		if ( ! allUserSet.containsKey(r.userID))
			allUserSet.put(r.userID, this);
	}
	
	
	/*
	 * Initialize a single instance
	 */
	User (int uid) {
		if (! allUserSet.containsKey(uid)) {
			userID = uid;
			records = new LinkedList<Record>();
			friends = new HashSet<Integer>();
			locs = new HashSet<Long>();
			
			try {
				BufferedReader fin = new BufferedReader(new FileReader(String.format("%s/%d", userDir, uid)));
				// add friends;
				String l = fin.readLine();
				String[] fids = l.split("\t");
				if (fids.length == 1 && Integer.parseInt(fids[0]) == -1)
					friends.clear();
				else {
					for (String fid : fids) {
						friends.add(Integer.parseInt(fid));
					}
				}
				// add records;
				while ((l = fin.readLine()) != null) {
					records.add(new Record(l));
				}
				fin.close();
			} catch (Exception e) {
				System.out.println("Exception in User constructor");
				e.printStackTrace();
			}
			allUserSet.put(uid, this);
		} else {
			this.userID = uid;
			this.records = allUserSet.get(uid).records;
			this.friends = allUserSet.get(uid).friends;
		}
		
		//totalweight = totalWeight();
	}
	
	
	public static void addAllUser() {
		File dir = new File(userDir);
		String[] fileNames = dir.list();
		for (String fn : fileNames) {
			new User(Integer.parseInt(fn));
		}
		System.out.println(String.format("Create %d users in total.", allUserSet.size()));
	}
	
	@Override
	public String toString() {
		return String.format("User %d: #friend -- %d; #records -- %d", userID, friends.size(), records.size());
	}
	
	
	HashSet<Long> getLocations() {
		if (locs.size() == 0) {
			for (Record r : records) {
				if ( ! locs.contains(r.locID) )
					locs.add( r.locID );
			}
		}
		return locs;
	}
	
	/**
	 * Calculate the weight of one location (represented by record) in this user's movement. </br>
	 * ==========================</br>
	 * The weight is calculated by </br>
	 * 			Prob_weight (loc_i ) = sum( e^(- distance ( loc_i, loc_j)) / n.
	 * @param rt  target record rt
	 * @return   the weight of this location
	 */
	public double locationWeight( Record rt ) {
		double weight = 0;
		double dist = 0;
		
		for (Record r : records) {
			dist = rt.distanceTo(r);
			dist = Math.exp(- User.para_c * dist);
			weight += dist;
		}
//		System.out.println(String.format("User %d  Cnt: %d, records size %d, weight %g", userID, cnt, records.size(), weight));
		weight /= records.size();
		
		return weight;
	}
	
	
	
	public static void main(String[] args) {
		for (int i = 0; i <= 335; i++) {
			User u0 = new User(i);	
			System.out.println(u0.getLocations().size());
		}
		//User.addAllUser();
	}


}
