package exp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Collections;

/***
 * Class DataPreProcessor
 * @author Hongjian
 * 
 * Split the raw data by individual user ID.
 * Count the visiting distribution of each location (by ID).
 * Count the #check-in distribution of each user.
 * Count the #friend distribution of each user.
 * 
 * Dependency:
 * 		Directly invoke class Record
 */
public class DataPreProcessor {
	static String rawCheckinsPath = "../../dataset/foursquare/FoursquareCheckins.csv";
	static String rawConnectionPath = "../../dataset/foursquare/FoursquareFriendship.csv";
	static String userDir = "../../dataset/foursquare/sorted/";
	static HashMap<Integer, LinkedList<Record>> users = new HashMap<Integer, LinkedList<Record>>();
	static HashMap<Integer, LinkedList<Integer>> friendship = new HashMap<Integer, LinkedList<Integer>>();
	
	
	
	//// Stage 1: Get sorted records for individual users.
	
	public DataPreProcessor() {
		long t_start = System.currentTimeMillis();
		System.out.println("Data pre-processing starts ...");
		BufferedReader fin = null;
		int c1=0, c2=0;
		try {
			// initialize all users
			fin = new BufferedReader(new FileReader(rawCheckinsPath));
			String l = fin.readLine();
			l = fin.readLine();
			while (l != null) {
				c1++;
				Record tmp = new Record(l);
				if (!users.containsKey(tmp.userID))
					users.put(tmp.userID, new LinkedList<Record>());
				users.get(tmp.userID).add(tmp);
				l = fin.readLine();
			}		
			fin.close();
			
			// initialize all friendships
			fin = new BufferedReader(new FileReader(rawConnectionPath));
			l = fin.readLine();
			l = fin.readLine();
			while (l != null) {
				c2++;
				String[] ls = l.split(",");
				int u1 = Integer.parseInt(ls[0]);
				int u2 = Integer.parseInt(ls[1]);
				if (!friendship.containsKey(u1))
					friendship.put(u1, new LinkedList<Integer>());
				if (!friendship.containsKey(u2))
					friendship.put(u2, new LinkedList<Integer>());
				friendship.get(u1).add(u2);
				friendship.get(u2).add(u1);
				l = fin.readLine();
			}
			fin.close();
		} catch (Exception e) {
			System.out.println(String.format("Line 1 %d\tLine 2 %d", c1, c2));
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Data pre-processing finished in %d seconds", (t_end - t_start)/1000));
	}
	
	
	
	public void generateUserFiles() {
		long t_start = System.currentTimeMillis();
		System.out.println("generateUserFiles starts ...");
		File dir = new File(userDir);
		dir.mkdir();
		for (int uid : users.keySet()) {
			try {
				BufferedWriter fout = new BufferedWriter(new FileWriter(userDir + Integer.toString(uid)));
				if (friendship.containsKey(uid))
					for (int fid : friendship.get(uid))
						fout.write(Integer.toString(fid) + "\t");
				else
					fout.write("-1");
				fout.write("\n");
				
				// sort the Records
				Collections.sort(users.get(uid));
				for (Record r : users.get(uid))
					fout.write(r + "\n");
				fout.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("generateUserFiles ends in %d seconds", (t_end-t_start)/1000));
	}
	
	
	public void userDistribution() {
		try {
			BufferedWriter fout = new BufferedWriter(new FileWriter("res/checkinsN-dist"));
			for (int uid : users.keySet())
				fout.write(Integer.toString(users.get(uid).size()) + "\n");
			fout.close();
			
			fout = new BufferedWriter(new FileWriter("res/friendsN-dist"));
			for (int uid : users.keySet()) {
				int cnt = 0;
				if (friendship.containsKey(uid))
					cnt = friendship.get(uid).size();
				fout.write(Integer.toString(cnt) + "\n");
			}
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) {
		DataPreProcessor dpp = new DataPreProcessor();
		// dpp.generateUserFiles();
//		dpp.userDistribution();
		System.out.println(String.format("#user: %d\t#user with friends: %d", users.size(), friendship.size()));
	}

}
