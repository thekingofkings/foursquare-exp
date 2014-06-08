package exp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;



public class User implements Comparable<User> {
	static HashMap<Integer, User> allUserSet = new HashMap<Integer, User>();
	static String userDir = "../../dataset/foursquare/sorted/";
	static double para_c = 20;
	
	
	int userID;
	LinkedList<Record> records;
	HashSet<Integer> friends;
	HashSet<String> locs;
	
	/*
	 * Construct the allUserSet (static field) in User class
	 */
	User( Record r ) {
		userID = r.userID;
		records = new LinkedList<Record>();
		friends = new HashSet<Integer>();
		locs = new HashSet<String>();
		
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
			locs = new HashSet<String>();
			
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
	
	
	public static void addTopkUser( int k ) {
		try {
			BufferedReader fin = new BufferedReader( new FileReader("res/userCheckins-rank.txt"));
			String l = null;
			int c = 0;
			while (( l = fin.readLine()) != null ) {
				String[] ls = l.split("\t");
				int uid = Integer.parseInt(ls[0]);
				new User(uid);
				c ++;
				if (c==k)
					break;
			}
			fin.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(String.format("%d users have been initailized.", k));
	}
	
	@Override
	public String toString() {
		return String.format("User %d: #friend -- %d; #records -- %d", userID, friends.size(), records.size());
	}
	
	
	HashSet<String> getLocations() {
		if (locs.size() == 0) {
			for (Record r : records) {
				if ( ! locs.contains(r.GPS()) )
					locs.add( r.GPS() );
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
//		for (int i = 0; i <= 335; i++) {
//			User u0 = new User(i);	
//			System.out.println(u0.getLocations().size());
//		}
//		User.addAllUser();
		getUserRecordsNRanking();
	}


	@Override
	public int compareTo(User oth) {
		return this.records.size() - oth.records.size();
	}

	private static void getUserRecordsNRanking() {
		User.addAllUser();
		List<User> users = new LinkedList<User>(User.allUserSet.values());
		Collections.sort(users);
		
		try {
			BufferedWriter fout = new BufferedWriter( new FileWriter( "res/userCheckins-rank.txt" ));
			for (int i = users.size() - 1; i >= 0; i--) {
				User u = users.get(i);
				u.getLocations();
				fout.write(String.format("%d\t%d\t%d%n", u.userID, u.records.size(), u.locs.size()));
			}
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
