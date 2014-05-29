package exp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;



/**
 * Class Tracker
 * ==================================
 * 
 * This class handles the various measures calculated from the location history of users.
 * The meeting event in this tracker is decided on the granularity of foursquare check-in IDs.
 * This will provide higher accuracy when decide whether two users are meeting or not.
 * 
 * All these measures are used to represent the closeness of two users. More specifically
 * we have the following measures:
 * 		1. Renyi entropy based co-locating places diversity
 * 		2. Frequency weighted by the location entropy
 * 		3. Mutual information between two users' historic locations
 * 		4. Interestingness score calculated by product of individual probability
 * 		5. Frequency	(the dominant factor)
 * 		6. Mutual entropy on co-locating places
 * 		7. Relative mutual entropy
 * 
 * 	
 *  @author Hongjian	
 */
public class Tracker {
	
	public static double distance_threshold = 0.03; // in km

	// overall measure
	static LinkedList<Integer> numOfColocations = new LinkedList<Integer>();
	
	/// frequent measure, filtered from above
	/*
	 * FrequentPair has three integer fields:
	 * 		user_a_id, user_b_id, colocation_size
	 */
	static ArrayList<int[]> FrequentPair = new ArrayList<int[]>();
	static ArrayList<HashSet<Long>> FrequentPair_CoLocations = new ArrayList<HashSet<Long>>();
	static HashMap<Long, Double> locationEntropy = new HashMap<Long, Double>();
	static HashMap<String, Double> GPSEntropy = new HashMap<String, Double>();

	// results on three features
	static LinkedList<Double> renyiDiversity = new LinkedList<Double>();
	static LinkedList<Double> weightedFreq = new LinkedList<Double>();
	static LinkedList<Double> mutualInfo = new LinkedList<Double>();
	static LinkedList<Double> interestingness = new LinkedList<Double>();
	static LinkedList<Integer> frequency = new LinkedList<Integer>();
	static LinkedList<Double> mutualEntroColoc = new LinkedList<Double>();
	static LinkedList<Double> mutualEntroColoc_v3 = new LinkedList<Double>();
	static LinkedList<Double> relaMutualEntro = new LinkedList<Double>();
	
	
	/**
	 * Initialize two packet private static field:
	 * 			FrequentPair
	 * 			FrequentPair_Colocation
	 * Also initialize the frequent users.
	 * 
	 * @param numUser -- number of users to be used in experiments (# frequent users)
	 */
	public static void initializeUsers() {
		long t_start = System.currentTimeMillis();
		System.out.println("Start initializeUsers.");
		User.addAllUser();
		long t_mid = System.currentTimeMillis();
		System.out.println(String.format("Initialize all users in %d seconds", (t_mid - t_start)/1000));
	}
	
	/**
	 * Count the number of co-locations for each pair.
	 */
	public static LinkedList<Integer> shareLocationCount() {
		long t_start = System.currentTimeMillis();
		System.out.println("shareLocationCount starts ...");
		HashMap<Integer, User> users = User.allUserSet;
		int cnt = 0;
		
		// get the first level iterator
		Object[] array = users.values().toArray();
		for (int i = 0; i < array.length; i++) {
			cnt ++;
			User ui = (User) array[i];

			// get the second level iterator
			if (ui.friends.size() > 0) {
				for (int j : ui.friends) {
					if (! users.containsKey(j)) {
						System.out.println(String.format("Error: friend ID %d does not exist!", j));
					} else {
						User uj = users.get(j);
						
						HashSet<Long> ui_loc = ui.getLocations();
						HashSet<Long> uj_loc = uj.getLocations();
						// get intersection of two sets
						HashSet<Long> colocations = new HashSet<Long>(ui_loc);
						colocations.retainAll(uj_loc);
						numOfColocations.add(colocations.size());
						
						// record the pair that share more than 1 common locations
						if (colocations.size() >= 1) {
							int[] p = new int[3];
							p[0] = ui.userID;
							p[1] = uj.userID;
							p[2] = colocations.size();
							FrequentPair.add(p);
							FrequentPair_CoLocations.add(colocations);
						}
					}	
				}
			}
			
			// monitor the process
			if (cnt % (users.size()/10) == 0)
				System.out.println(String.format("Process - shareLocationCount finished %d0%%", cnt/(users.size()/10)));
		}
		// output
		try {
			BufferedWriter fout = new BufferedWriter(new FileWriter("res/colocation-cnt.txt"));
			BufferedWriter foutpair = new BufferedWriter(new FileWriter("res/pair-meetcnt.txt"));
		
			for (int i : numOfColocations) {
				fout.write(Integer.toString(i) + "\n");
			}
			
			for (int[] j : FrequentPair) {
				foutpair.write(Integer.toString(j[0]) + " " + Integer.toString(j[1]) + " "
						+ Integer.toString(j[2]) + "\n" );
			}
			fout.close();
			foutpair.close();
			} catch (IOException e) {
				e.printStackTrace();
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Finish shareLocationCount in %d seconds", (t_end - t_start)/1000));
		return numOfColocations;
	}
	
	
	/**
	 * write out the possible co-locations for each pair
	 */
	@SuppressWarnings("unused")
	private static void writeOutPairColocations() {
		System.out.println("Start writeOutPairColocations.");
		try {
			BufferedWriter fout = new BufferedWriter(new FileWriter("res/colocations.txt"));
			String l = null;
			for (User ua : User.allUserSet.values()) {
				for (int ubid : ua.friends) {
					if (ubid > ua.userID) {
						User ub = User.allUserSet.get(ubid);
						// get intersection of two sets
						HashSet<Long> ua_loc = ua.getLocations();
						HashSet<Long> ub_loc = ub.getLocations();
						ua_loc.retainAll(ub_loc);
						
						for (long loc : ua_loc)
							fout.write(loc + "\t");
						fout.write("\n");
					}
				}
			}
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Process writeOutPairColocations ends.");
	}
	
	/*
	 * ===============================================================
	 * The first feature: diversity
	 * ===============================================================
	 * 
	 */
	
	/**
	 * Implement the first feature in SIGMOD'13
	 * The Renyi Entropy based diversity.
	 */
	public static LinkedList<Double> RenyiEntropyDiversity() {
		long t_start = System.currentTimeMillis();
		int c1 = 0, c2 = 0;
		double avg_freq1 = 0, avg_freq2 = 0;
		for (int i = 0; i < FrequentPair.size(); i++) {
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			// 1. calculate the number of co-occurrence on each co-locating places
			HashMap<Long, Integer> coloc = coLocationFreq (uaid, ubid);
			
			int sum = 0;
			for (int f : coloc.values()) {
				sum += f;
			}

			// 2. calculate the probability of co-occurrence
			double[] prob = new double[coloc.size()];
			int ind = 0;
			for (int f : coloc.values()) {
				prob[ind] = (double) f / sum;
				ind ++;
			}
			
			// 3. calculate the Renyi Entropy (q = 0.1)
			double renyiEntropy = 0;
			for (int j = 0; j < coloc.size(); j++) {
				renyiEntropy += Math.pow(prob[j], 0.1);
			}
			renyiEntropy = Math.log(renyiEntropy) / 0.9;
			// 4. calculate diversity
			double divs = Math.exp(renyiEntropy);
			if (sum > 1)
				if (sum != coloc.size()) {
					c1 ++;
					avg_freq2 += sum;
				} else {
					c2 ++;
					avg_freq1 += sum;
				}
			renyiDiversity.add(divs);
		}
		System.out.println(String.format("uniform: %d pair, %g\t non-unif %d pairs, %g", c2, avg_freq2 / (double)c2, c1, avg_freq1 / (double) c1));
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Renyi entropy based diversity (%d pair) found in %d seconds!", renyiDiversity.size(), (t_end - t_start)/1000));
		return renyiDiversity;
	}
	
	
	/**
	 * Assistant function for Renyi entropy based diversity.
	 * calculate the colocation frequency given two user IDs and the location.
	 * {@meeting criteria: location ID}
	 * 
	 * the cnt[] is the meeting frequency at each different locations
	 * the return value (sum) is the totoal meeting frequency
	 */
	private static HashMap<Long, Integer> coLocationFreq( int user_a_id, int user_b_id) {
		LinkedList<Record> ras = User.allUserSet.get(user_a_id).records;
		LinkedList<Record> rbs = User.allUserSet.get(user_b_id).records;
		HashMap<Long, Integer> loc_cnts = new HashMap<Long, Integer>();
		
		// find records of user_a in colocating places
		int aind = 0;
		int bind = 0;
		long last_Meet = 0;
		while (aind < ras.size() && bind < rbs.size()) {
			Record ra = ras.get(aind);
			Record rb = rbs.get(bind);
			
			// count the frequency
			if (ra.timestamp - rb.timestamp > 3600 * 4) {
				bind ++;
				continue;
			} else if (rb.timestamp - ra.timestamp > 3600 * 4) {
				aind ++;
				continue;
			} else {
				if (ra.locID == rb.locID && ra.timestamp  - last_Meet >= 3600) {
//				if (ra.distanceTo(rb) < distance_threshold && ra.timestamp - last_Meet >= 3600 ) {
					if (loc_cnts.containsKey(ra.locID)) {
						int f = loc_cnts.get(ra.locID);
						loc_cnts.put(ra.locID, f+1);
					} else {
						loc_cnts.put(ra.locID, 1);
					}
					last_Meet = ra.timestamp;
				}
				aind ++;
				bind ++;
			}
			
		}
		return loc_cnts;
	}
	
	
	/*
	 * ===============================================================
	 * The second feature: weighted frequency
	 * ===============================================================
	 * 
	 */
	
	/**
	 * initial friend pair from external file, which is generated by CaseFinder class
	 * @param topk
	 */
	private static void initialTopKPair(int topk) {
		System.out.println("Start initialTopKPair.");
		try {
			BufferedReader fin = new BufferedReader(new FileReader(String.format("topk_freqgt1-%d.txt", topk)));
			String l = null;
			BufferedReader fin2 = new BufferedReader(new FileReader(String.format("topk_colocations-%d.txt", topk)));
			String l2 = null;
			int c1 = 0;
			int c2 = 0;
			while ( (l=fin.readLine()) != null ) {
				c1 ++;
				String[] ls = l.split("\\s+");
				if (Integer.parseInt(ls[2]) > 0) {
					int uaid = Integer.parseInt(ls[0]);
					int ubid = Integer.parseInt(ls[1]);
					new User(uaid);
					new User(ubid);
					int friFlag = Integer.parseInt(ls[3]);
					int[] fp = {uaid, ubid, friFlag};
					FrequentPair.add(fp);
					
					while (c2 < c1) {
						c2 ++;
						l2 = fin2.readLine();
						if (c2 == c1) {
							HashSet<Long> colocs = new HashSet<Long>();
							String[] ls2 = l2.split("\\s+");
							for (String s : ls2)
								colocs.add(Long.parseLong(s));
							FrequentPair_CoLocations.add(colocs);
						}
					}
				}
			}
			fin.close();
			fin2.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Process initialTopKPair ends.");
	}
	
	
	
	/**
	 * Implement the second feature of SIGMOD'13
	 * The weighted frequency (weight by location entropy)
	 */
	public static LinkedList<Double> weightedFrequency() {
		long t_start = System.currentTimeMillis();
		/*
		 * The calculation of location entropy is not efficient.
		 * The original execution time in HP laptop is 470s.
		 * After we factor out the location entropy calculation, 
		 * now the execution time in HP is 4s.
		 */
		locationEntropy = readLocationEntropyIDbased();
		for (int i = 0; i < FrequentPair.size(); i++) {
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			// 1. calculate the frequency of co-occurrence on each co-locating places
			HashMap<Long, Integer> coloc = coLocationFreq (uaid, ubid);
			
			// only consider people with meeting events
//			if (coloc.size() > 0) {
				double weightedFrequency = 0;
				int frequen = 0;
				for (int f : coloc.values())
					frequen += f;
				frequency.add(frequen);
				
				// 2. calculate location entropy
				for (long locid : coloc.keySet()) {
					// the locationEntropy should contain this locid Key, otherwise we should use 0 as its entropy
					weightedFrequency += coloc.get(locid) * Math.exp(- locationEntropy.get(locid));
				}
				weightedFreq.add(weightedFrequency);
//			}
			
				
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - weightedFrequency finished %d0%%", i/(FrequentPair.size()/10)));
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Weighted frequency (%d pairs) found in %d seconds", weightedFreq.size(), (t_end - t_start)/1000));
		return weightedFreq;
	}

	
	
	private static void writePairMeasure() {
		System.out.println("Start writePairMeasure");
		System.out.println(String.format("%d %d %d %d", renyiDiversity.size(), weightedFreq.size(), frequency.size(), FrequentPair.size()));
		long t_start = System.currentTimeMillis();
		try {
			BufferedWriter fout = new BufferedWriter(new FileWriter("res/sigmod13.txt"));
			for (int i = 0; i < FrequentPair.size(); i++) {
				int uaid = FrequentPair.get(i)[0];
				int ubid = FrequentPair.get(i)[1];
				// id_a, id_b, co-locatoin entropy, weighted frequency, frequency, friends flag
				fout.write(String.format("%d\t%d\t%g\t%g\t%d\t%d\n", uaid, ubid, renyiDiversity.get(i), weightedFreq.get(i), frequency.get(i), FrequentPair.get(i)[2]));
			}
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Process writePairMeasure finished in %d secondes.", (t_end - t_start)/1000) );
	}

	
	//===============================location entropy begins===================================
	/**
	 * Assistant function
	 * -- calculate location entropy for one specific location (Shannon Entropy)
	 */
	private static HashMap<Long, Double> locationEntropyIDbased() {
		long t_start = System.currentTimeMillis();
		HashMap<Long, Double> loc_entro = new HashMap<Long, Double>();
		HashMap<Long, HashMap<Integer, Integer>> loc_user_visit = new HashMap<Long, HashMap<Integer, Integer>>();
		HashMap<Long, Integer> loc_total_visit = new HashMap<Long, Integer>();
		
		// 1. get the location visiting frequency
		for (User u : User.allUserSet.values()) {
			for (Record r : u.records) {
				// count individual user visiting
				if (loc_user_visit.containsKey(r.locID)) {
					if (loc_user_visit.get(r.locID).containsKey(u.userID)) {
						int freq = loc_user_visit.get(r.locID).get(u.userID);
						loc_user_visit.get(r.locID).put(u.userID, freq + 1);
					} else {
						loc_user_visit.get(r.locID).put(u.userID, 1);
					}
				} else {
					loc_user_visit.put(r.locID, new HashMap<Integer, Integer>());
					loc_user_visit.get(r.locID).put(u.userID, 1);
				}
				// count total visiting for one location
				if (loc_total_visit.containsKey(r.locID)) {
					int f = loc_total_visit.get(r.locID);
					loc_total_visit.put(r.locID, f+1);
				} else {
					loc_total_visit.put(r.locID, 1);
				}
			}
		}
		// 2. calculate the per user probability
		for (Long locid : loc_user_visit.keySet()) {
			double locEntropy = 0;
			for (int uid : loc_user_visit.get(locid).keySet()) {
				if (loc_user_visit.get(locid).size() > 1) {	// if there is only one user visit this locatin, then its entropy is 0, and there won't be any meeting events.
					if (loc_user_visit.get(locid).get(uid) > 0) {
						double prob = (double) loc_user_visit.get(locid).get(uid) / loc_total_visit.get(locid);
						locEntropy += - prob * Math.log(prob);
					}
				}
			}
			loc_entro.put(locid, locEntropy);
		}
		// 3. return the entropy
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Size of loc_entropy: %d.\n locationEntropyIDbased finished in %d seconds.", loc_entro.size(), (t_end - t_start)/1000));
		return loc_entro;
	}
	
	
	private static HashMap<String, Double> locationEntropyGPSbased() {
		long t_start = System.currentTimeMillis();
		HashMap<String, Double> loc_entro = new HashMap<String, Double>();
		HashMap<String, HashMap<Integer, Integer>> loc_user_visit = new HashMap<String, HashMap<Integer, Integer>>();
		HashMap<String, Integer> loc_total_visit = new HashMap<String, Integer>();

		// 1. get the GPS visiting frequency
		for (User u : User.allUserSet.values()) {
			for (Record r : u.records) {
				// count individual user visiting
				if (loc_user_visit.containsKey(r.GPS())) {
					if (loc_user_visit.get(r.GPS()).containsKey(u.userID)) {
						int freq = loc_user_visit.get(r.GPS()).get(u.userID);
						loc_user_visit.get(r.GPS()).put(u.userID, freq + 1);
					} else {
						loc_user_visit.get(r.GPS()).put(u.userID, 1);
					}
				} else {
					loc_user_visit.put(r.GPS(), new HashMap<Integer, Integer>());
					loc_user_visit.get(r.GPS()).put(u.userID, 1);
				}
				// count total visiting for on location
				if (loc_total_visit.containsKey(r.GPS())) {
					int f = loc_total_visit.get(r.GPS());
					loc_total_visit.put(r.GPS(), f+1);
				} else {
					loc_total_visit.put(r.GPS(), 1);
				}
			}
		}
		// 2. calculate the per user probability
		for (String gps : loc_user_visit.keySet()) {
			double locEntropy = 0;
			for (int uid : loc_user_visit.get(gps).keySet()) {
				if (loc_user_visit.get(gps).size() > 1)
					if (loc_user_visit.get(gps).get(uid) > 0) {
						double prob = (double) loc_user_visit.get(gps).get(uid) / loc_total_visit.get(gps);
						locEntropy += - prob * Math.log(prob);
					}
			}
			loc_entro.put(gps, locEntropy);
		}
		// 3. return the entropy
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Size of loc_entropy: %d.\n locationEntropyGPSbased finished in %d seconds.", loc_entro.size(), (t_end - t_start)/1000));
		return loc_entro;
	}
	
	
	/**
	 * calculate the location entropy using the records of given number of top users
	 * 
	 * @param IDflag -- true to use location ID, false to use GPS
	 * @param sampleRate -- integer of n in __%
	 *  
	 */
	public static void writeLocationEntropy(boolean IDflag, int sampleRate) {
		// initialize users
		int numUser = User.allUserSet.size();
		Random r = new Random();
		try {
			File dir = new File(User.userDir);
			String[] fileNames = dir.list();
			User.allUserSet.clear();
			int c = 0;
			for (String fid : fileNames) {
				c++;
				if (r.nextDouble() <= sampleRate / 100.0 ) {
					int uid = Integer.parseInt(fid);
					new User(uid);
					if (User.allUserSet.size() == sampleRate / 100.0 * numUser)
						break;
				}
			}
			System.out.println(String.format("Total user %d\t%d", c, User.allUserSet.size()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (IDflag == true) {
			// calculate location entropy
			locationEntropy = locationEntropyIDbased();
		} else {
			GPSEntropy = locationEntropyGPSbased();
		}
		// write out location entropy
		try {
			BufferedWriter fout;
			if (IDflag == true) {
				if (sampleRate <= 100) {
					fout = new BufferedWriter(new FileWriter(String.format("res/locationEntropy-%ds.txt", sampleRate)));
				} else {
					fout = new BufferedWriter(new FileWriter("res/locationEntropy.txt"));
				}
				for (long loc : locationEntropy.keySet())
					fout.write(String.format("%d\t%g\n", loc, locationEntropy.get(loc)));
			} else {
				fout = new BufferedWriter(new FileWriter(String.format("res/GPSEntropy-%ds.txt", sampleRate)));
				for (String gps : GPSEntropy.keySet())
					fout.write(String.format("%s\t%g\n", gps, GPSEntropy.get(gps)));
			}
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	
	public static void writeLocationEntropy(boolean IDflag) {
		writeLocationEntropy(IDflag, 100);
	}
	
	/**
	 * read in the location entropy from corresponding file</br>
	 * If we have the location entropy file of given number of users, then this function will work, otherwise
	 * it will throw an exception.
	 * 
	 * @param sampleRate -- an integer means dd%. if sampleRate > 100, use the 100% of top 5000 users
	 * @return
	 */
	public static HashMap<Long, Double> readLocationEntropyIDbased(int sampleRate) {
		if (locationEntropy.isEmpty()) {
			try {
				BufferedReader fin;
				if (sampleRate <= 100) {
					fin = new BufferedReader( new FileReader(String.format("res/locationEntropy-%ds.txt", sampleRate)));
					System.out.println(String.format("File locationEntropy-%ds.txt found!", sampleRate));
				} else { 
					fin = new BufferedReader( new FileReader("res/locationEntropy.txt"));
					System.out.println("File locationEntropy.txt found!");
				}
				
				String l = null;
				while ((l = fin.readLine()) != null) {
					String[] ls = l.split("\\s+");
					long loc = Long.parseLong(ls[0]);
					double entropy = Double.parseDouble(ls[1]);
					locationEntropy.put(loc, entropy);
				}
				fin.close();
			} catch (FileNotFoundException e) {
				System.out.println("No location entropy file found. Generate new one ...");
				writeLocationEntropy(true, sampleRate);	// true use location ID, false to use GPS
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(String.format("Location entropy size %d.", locationEntropy.size()));
		}
		
		return locationEntropy;
	}
	
	public static HashMap<Long, Double> readLocationEntropyIDbased() {
		return readLocationEntropyIDbased(101);
	}
	
	
	public static HashMap<String, Double> readLocationEntropyGPSbased( int sampleRate ) {
		if (GPSEntropy.isEmpty()) {
			try {
				BufferedReader fin = new BufferedReader( new FileReader(String.format("res/GPSEntropy-%ds.txt", sampleRate)));
				String l = null;
				while ( (l=fin.readLine()) != null) {
					String[] ls = l.split("\\s+");
					String gps = ls[0];
					double entropy = Double.parseDouble(ls[1]);
					if (! GPSEntropy.containsKey(gps))
						GPSEntropy.put(gps, entropy);
				}
				fin.close();
			} catch (FileNotFoundException e) {
				System.out.println("No GPS entropy file found. Generate a new one ...");
				writeLocationEntropy(false);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println(String.format("GPS location size %d.", GPSEntropy.size()));
		}
		return GPSEntropy;
	}
	//===================================location entropy ends=======================================
	
	/*
	 * ===============================================================
	 * The third feature: mutual information
	 * ===============================================================
	 * 
	 */
	
	/**
	 * use mutual information between two users to measure their correlation
	 * Here we use the formula:
	 * 		I(x,y) = H(x) + H(y) - H(x,y)
	 * to calculate the mutual entropy
	 * 
	 * This mutual information is calculated w.r.t. the complete location set.
	 */
	public static LinkedList<Double> mutualInformation() {
		long t_start = System.currentTimeMillis();
		/*
		 * Speed boost
		 * the old method is not efficient, because it is not necessary to calculate the 
		 * marginal entropy repeatedly.
		 */
		HashMap<Integer, Double> entro = new HashMap<Integer,Double>();
		for (int i = 0; i < FrequentPair.size(); i++) {
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			// 1. calculate the marginal entropy of user a
			double entroA;
			if (entro.containsKey(uaid))
				entroA = entro.get(uaid);
			else {
				entroA = marginalEntropy(uaid);
				entro.put(uaid, entroA);
			}
			// 2. calculate the marginal entropy of user b
			double entroB;
			if (entro.containsKey(ubid))
				entroB = entro.get(ubid);
			else {
				entroB = marginalEntropy(ubid);
				entro.put(ubid, entroB);
			}
			// 3. joint entropy of A and B
			double jointEntro = jointEntropy(uaid, ubid);
			// 4. mutual information
			mutualInfo.add(entroA + entroB - jointEntro);
			
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - mutualInformation finished %d0%%", i/(FrequentPair.size()/10)));
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Calculate mutual inforamtion in %d seconds", (t_end - t_start)/1000));
		return mutualInfo;
	}
	
	/**
	 * Assistant function for mutual entropy 
	 * -- calculate marginal entropy w.r.t. the complete historical locations
	 */
	private static double marginalEntropy(int uid) {
		User u = User.allUserSet.get(uid);
		HashMap<Long, Integer> locFreq = new HashMap<Long, Integer>();
		// 1. count frequency
		int totalLocationNum = u.records.size();
		for (Record r : u.records) {
			if (locFreq.containsKey(r.locID))
				locFreq.put(r.locID, locFreq.get(r.locID) + 1);
			else
				locFreq.put(r.locID, 1);
		}
		// 2. probability and entropy
		double prob = 0;
		double entro = 0;
		for (Long i : locFreq.keySet()) {
			prob = (double) locFreq.get(i) / totalLocationNum;
			entro += - prob * Math.log(prob);
		}
		return entro;
	}
	
	/**
	 * Assistant function for mutual entropy -- calculate joint entropy
	 * 
	 * One problem is whether use the synchronized time series to index the records.
	 * This dataset is the checkin data from gowalla, which is really sparse.
	 * The record interpolation is impossible. Generally, each user have less than 1
	 * check-in record each day.
	 * 
	 * My solution is to go through all records, then determine whether their timestamp
	 * are within one timeslot.
	 * 
	 * Even though, the observation on both users at the same time slot is an rare events.
	 * Therefore, I have to use the permutation to produce more fundamental event to 
	 * approximate the ground truth.
	 */
	private static double jointEntropy( int uaid, int ubid ) {
		User a = User.allUserSet.get(uaid);
		User b = User.allUserSet.get(ubid);
		HashMap<Long, HashMap<Long, Integer>> locFreq = new HashMap<>();
		
		// 1. count frequency of multi-variables distribution
		int totalCase = 0;
		for (Record ar : a.records) {
			for (Record br : b.records) {
				// ar and br are in the same time slots
				//if (Math.abs(ar.timestamp - br.timestamp) <= 4 * 3600) {
					// put that observation at the same timeslot into the two level maps
					if (locFreq.containsKey(ar.locID) && locFreq.get(ar.locID).containsKey(br.locID)) {
						int f = locFreq.get(ar.locID).get(br.locID) + 1;
						locFreq.get(ar.locID).put(br.locID, f);
						totalCase ++;
					}
					else if (locFreq.containsKey(ar.locID) && ! locFreq.get(ar.locID).containsKey(br.locID)) {
						locFreq.get(ar.locID).put(br.locID, 1);
						totalCase ++;
					}
					else if (!locFreq.containsKey(ar.locID)) {
						locFreq.put(ar.locID, new HashMap<Long, Integer>());
						locFreq.get(ar.locID).put(br.locID, 1);
						totalCase ++;
					}
				//}
			}
		}
		//System.out.println(String.format("Total case of joint entropy %d", totalCase));
		// 2. probability and entropy
		double prob = 0;
		double entro = 0;
		for (Long i : locFreq.keySet()) {
			for (Long j : locFreq.get(i).keySet()) {
				prob = (double) locFreq.get(i).get(j) / totalCase;
				entro += - prob * Math.log(prob);
			}
		}
		return entro;
	}
	
	
	/**
	 * Calculate mutual information from the definition:
	 * 		I(x,y) = \sum \sum p(x,y) log (p(x,y) / (p(x)p(y)) )
	 */
	public static LinkedList<Double> mutualInformation_v2() {
		long t_start = System.currentTimeMillis();
		// 1. calculate the individual probability
		HashMap<Integer, HashMap<Long, Double>> user_loc_prob = new HashMap<Integer, HashMap<Long, Double>>();
		for (int[] p : FrequentPair) {
			for (int i = 0; i < 2; i++ ) {
				int uid = p[i];
				for (Record ra : User.allUserSet.get(uid).records) {
					if (user_loc_prob.containsKey(uid)) {
						if (user_loc_prob.get(uid).containsKey(ra.locID)) {
							double f = user_loc_prob.get(uid).get(ra.locID);
							user_loc_prob.get(uid).put(ra.locID, f + 1);
						} else {
							user_loc_prob.get(uid).put(ra.locID, 1.0);
						}
					} else {
						user_loc_prob.put(uid, new HashMap<Long, Double>());
						user_loc_prob.get(uid).put(ra.locID, 1.0);
					}
				}
				int cnt = User.allUserSet.get(uid).records.size();
				for (Long locid : user_loc_prob.get(uid).keySet()) {
					double freq = user_loc_prob.get(uid).get(locid);
					user_loc_prob.get(uid).put(locid, freq / cnt);
				}
			}
		}
		
		for (int i = 0; i < FrequentPair.size(); i++ ) {
			// 2. calculate the pair frequency
			HashMap<Long, HashMap<Long, Double>> pairLocProb = new HashMap<>();
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			int totalCase = 0;
			for (Record ar : User.allUserSet.get(uaid).records) {
				for (Record br : User.allUserSet.get(ubid).records) {
					if (pairLocProb.containsKey(ar.locID) && pairLocProb.get(ar.locID).containsKey(br.locID)) {
						double f = pairLocProb.get(ar.locID).get(br.locID) + 1;
						pairLocProb.get(ar.locID).put(br.locID, f);
						totalCase ++;
					}
					else if (pairLocProb.containsKey(ar.locID) && ! pairLocProb.get(ar.locID).containsKey(br.locID)) {
						pairLocProb.get(ar.locID).put(br.locID, 1.0);
						totalCase ++;
					}
					else if (!pairLocProb.containsKey(ar.locID)) {
						pairLocProb.put(ar.locID, new HashMap<Long, Double>());
						pairLocProb.get(ar.locID).put(br.locID, 1.0);
						totalCase ++;
					}
				}
			}
			double mutualE = 0;
			for (Long l1 : pairLocProb.keySet()) {
				for (Long l2: pairLocProb.get(l1).keySet()) {
					// 3. calculate the pair probability
					double f = pairLocProb.get(l1).get(l2);
					double pairProb = f / totalCase;
					// 4. calculate the mutual information
					mutualE += pairProb * Math.log(pairProb / user_loc_prob.get(uaid).get(l1) / user_loc_prob.get(ubid).get(l2));
				}
			}
			mutualInfo.add(mutualE);
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - mutualInformation_v2 finished %d0%%", i/(FrequentPair.size()/10)));
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("mutualInformation_v2 executed for %d seconds", (t_end-t_start)/1000));
		return mutualInfo;
	}
	
	/*
	 * ==========================================================================
	 * The fourth feature: Fei's interestingness
	 * ==========================================================================
	 * 
	 */
	
	/**
	 * Calculate the interestingness score defined by Fei's PAKDD submission.
	 */
	public static LinkedList<Double> interestingnessPAKDD() {
		long t_start = System.currentTimeMillis();
		for (int i = 0; i < FrequentPair.size(); i++ ) {
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			HashSet<Long> colocs = FrequentPair_CoLocations.get(i);
			// 1. calculate the colocating interestness
			double Finterest = coLocationScore(uaid, ubid, colocs);
			interestingness.add(Finterest);
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - interestingnessPAKDD finished %d0%%", i/(FrequentPair.size()/10)));	
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("Interestingness score found in %d seconds", (t_end - t_start)/1000));
		return interestingness;
	}
	
	/**
	 * calculate the co-location score directly
	 */
	private static double coLocationScore( int user_a_id, int user_b_id, HashSet<Long> colocs ) {
		LinkedList<Record> ra = User.allUserSet.get(user_a_id).records;
		HashMap<Long, LinkedList<Record>> raco = new HashMap<Long, LinkedList<Record>>();
		LinkedList<Record> rb = User.allUserSet.get(user_b_id).records;
		HashMap<Long, LinkedList<Record>> rbco = new HashMap<Long, LinkedList<Record>>();
		double interest = 0;
		// System.out.println(String.format("Colocs size %d", colocs.size()));
		// find the reverse map of location count
		for (Record r : ra) {
			if (raco.containsKey(r.locID)) {
				raco.get(r.locID).add(r);
			} else {
				raco.put(r.locID, new LinkedList<Record>());
				raco.get(r.locID).add(r);
			}
		}
		for (Record r : rb) {
			if (rbco.containsKey(r.locID)) {
				rbco.get(r.locID).add(r);
			} else {
				rbco.put(r.locID, new LinkedList<Record>());
				rbco.get(r.locID).add(r);
			}
		}
		for (Long loc_id : colocs) {
			// judge their co-locating events with time
			for (Record r1 : raco.get(loc_id)) {
				for (Record r2 : rbco.get(loc_id)) {
					// We identify the co-locating event with a 4-hour time window
					if (r1.timestamp - r2.timestamp <= 3600 * 4)
						interest += - Math.log( (double) raco.get(loc_id).size() / ra.size()) 
							- Math.log( (double) rbco.get(loc_id).size() / rb.size() );
				}
			}
		}
		return interest;
	}
	
	
	
	/*
	 * ======================================================
	 * The fifth feature: Mutual entropy on Co-locations
	 * ======================================================
	 * 
	 * We still use the mutual entropy:
	 * 		I(X,Y) = H(X) + H(Y) - H(X,Y)
	 * 
	 * But, the difference between this feature and the third feature is that
	 * we only focus on the set of locations where two users co-locate.
	 * 
	 */
	
	public static LinkedList<Double> mutualEntropyOnColocation() {
		long t_start = System.currentTimeMillis();
		// calculate the marginal entropy of each user only once.
		HashMap<Integer, Double> entro = new HashMap<Integer, Double>();
		for (int i = 0; i < FrequentPair.size(); i++) {
			// get two user
			User a = User.allUserSet.get(FrequentPair.get(i)[0]);
			User b = User.allUserSet.get(FrequentPair.get(i)[1]);
			HashSet<Long> locs = FrequentPair_CoLocations.get(i);
			// 1. calculate the marginal entropy of U_a over co-locations
			double entroA = 0;
			if (entro.containsKey(a.userID)) {
				entroA = entro.get(a.userID);
			} else {
				entroA = marginalEntropy(a.userID, locs );
				entro.put(a.userID, entroA);
			}
			// 2. calculate the marginal entropy of U_b over co-locations
			double entroB = 0;
			if (entro.containsKey(b.userID)) {
				entroB = entro.get(b.userID);
			} else {
				entroB = marginalEntropy(b.userID, locs);
				entro.put(b.userID, entroB);
			}
			// 3. calculate the joint entropy of U_a and U_b over 
			double joint_entro = jointEntropy(a.userID, b.userID, locs);
			mutualEntroColoc.add(entroA + entroB - joint_entro);
			
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - mutualEntropyOnColocation finished %d0%%", i/(FrequentPair.size()/10)));
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("mutualEntropyOnColocation executed %d seconds", (t_end - t_start)/1000));
		return mutualEntroColoc;
	}
	
	/** 
	 * Assistant function for mutual information calculation
	 * 
	 * -- calculate the marginal entropy of one user over the given location set
	 */
	private static double marginalEntropy(int uid, HashSet<Long> locs) {
		User u = User.allUserSet.get(uid);
		HashMap<Long, Integer> locFreq = new HashMap<Long, Integer>();
		// 1. count frequency on each locations in location set
		int totalLocationNum = 0;
		for (Record r : u.records) {
			if (locs.contains(r.locID)) {
				if (locFreq.containsKey(r.locID)) {
					totalLocationNum ++;
					locFreq.put(r.locID, locFreq.get(r.locID) + 1);
				}
				else {
					totalLocationNum ++;
					locFreq.put(r.locID, 1);
				}
			}
		}
		
		// 2. probability and entropy
		double prob = 0;
		double entro = 0;
		for (Long i : locFreq.keySet()) {
			prob = (double) locFreq.get(i) / totalLocationNum;
			entro += - prob * Math.log(prob);
		}
		return entro;
	}
	
	/**
	 * Assistant function for joint information calculation
	 * 
	 * -- calculate the joint entropy of two users over the given location set
	 */
	private static double jointEntropy( int uaid, int ubid, HashSet<Long> locs ) {
		User a = User.allUserSet.get(uaid);
		User b = User.allUserSet.get(ubid);
		HashMap<Long, HashMap<Long, Integer>> locFreq = new HashMap<Long, HashMap<Long, Integer>>();
		// 1. count frequency over given location set
		int totalCase = 0;
		for (Record ar : a.records) {
			for (Record br : b.records) {
				// Two records in same timeslot
				//if (Math.abs(ar.timestamp-br.timestamp) <= 4 * 3600) {
					// count frequency only on the target set
					if (locs.contains(ar.locID) && locs.contains(br.locID)) {
						if (locFreq.containsKey(ar.locID) && locFreq.get(ar.locID).containsKey(br.locID)) {
							int f = locFreq.get(ar.locID).get(br.locID) + 1;
							locFreq.get(ar.locID).put(br.locID, f);
							totalCase ++;
						}
						else if (locFreq.containsKey(ar.locID) && ! locFreq.get(ar.locID).containsKey(br.locID)) {
							locFreq.get(ar.locID).put(br.locID, 1);
							totalCase ++;
						}
						else if (!locFreq.containsKey(ar.locID)) {
							locFreq.put(ar.locID, new HashMap<Long, Integer>());
							locFreq.get(ar.locID).put(br.locID, 1);
							totalCase ++;
						}
					}
				//}
			}
		}
		// 2. probability and entropy
		double prob = 0;
		double entro = 0;
		for (Long i : locFreq.keySet()) {
			for (Long j : locFreq.get(i).keySet()) {
				prob = (double) locFreq.get(i).get(j) / totalCase;
				entro += - prob * Math.log(prob);
			}
		}
		return entro;
	}
	
	
	
	/**
	 * Calculate mutual information over co-locations given set from the definition:
	 * 		I(x,y) = \sum \sum p(x,y) log (p(x,y) / (p(x)p(y)) )
	 */
	public static LinkedList<Double> mutualEntropyOnColocation_v2() {
		long t_start = System.currentTimeMillis();
		// 1. calculate the individual probability
		HashMap<Integer, HashMap<Long, Double>> user_loc_prob = new HashMap<Integer, HashMap<Long, Double>>();
		for (int i = 0; i < FrequentPair.size(); i++) {
			int[] p = FrequentPair.get(i);
			// get given target locatoin set
			HashSet<Long> locs = FrequentPair_CoLocations.get(i);
			for (int j = 0; j < 2; j++ ) {
				int uid = p[j];
				int cnt = 0;
				for (Record ra : User.allUserSet.get(uid).records) {
					if (locs.contains(ra.locID)) {
						if (user_loc_prob.containsKey(uid)) {
							if (user_loc_prob.get(uid).containsKey(ra.locID)) {
								double f = user_loc_prob.get(uid).get(ra.locID);
								user_loc_prob.get(uid).put(ra.locID, f + 1);
							} else {
								user_loc_prob.get(uid).put(ra.locID, 1.0);
							}
						} else {
							user_loc_prob.put(uid, new HashMap<Long, Double>());
							user_loc_prob.get(uid).put(ra.locID, 1.0);
						}
						cnt ++;
					}
				}
				
				for (Long locid : user_loc_prob.get(uid).keySet()) {
					double freq = user_loc_prob.get(uid).get(locid);
					user_loc_prob.get(uid).put(locid, freq / cnt);
				}
			}
		}
		
		for (int i = 0; i < FrequentPair.size(); i++ ) {
			// 2. calculate the pair frequency
			HashMap<Long, HashMap<Long, Double>> pairLocProb = new HashMap<>();
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			HashSet<Long> locs = FrequentPair_CoLocations.get(i);
			int totalCase = 0;
			for (Record ar : User.allUserSet.get(uaid).records) {
				for (Record br : User.allUserSet.get(ubid).records) {
					if (locs.contains(ar.locID) && locs.contains(br.locID)) {
						if (pairLocProb.containsKey(ar.locID) && pairLocProb.get(ar.locID).containsKey(br.locID)) {
							double f = pairLocProb.get(ar.locID).get(br.locID) + 1;
							pairLocProb.get(ar.locID).put(br.locID, f);
							totalCase ++;
						}
						else if (pairLocProb.containsKey(ar.locID) && ! pairLocProb.get(ar.locID).containsKey(br.locID)) {
							pairLocProb.get(ar.locID).put(br.locID, 1.0);
							totalCase ++;
						}
						else if (!pairLocProb.containsKey(ar.locID)) {
							pairLocProb.put(ar.locID, new HashMap<Long, Double>());
							pairLocProb.get(ar.locID).put(br.locID, 1.0);
							totalCase ++;
						}
					}
				}
			}
			double mutualE = 0;
			for (Long l1 : pairLocProb.keySet()) {
				for (Long l2: pairLocProb.get(l1).keySet()) {
					// 3. calculate the pair probability
					double f = pairLocProb.get(l1).get(l2);
					double pairProb = f / totalCase;
					// 4. calculate the mutual information
					mutualE += pairProb * Math.log(pairProb / user_loc_prob.get(uaid).get(l1) / user_loc_prob.get(ubid).get(l2));
				}
			}
			mutualEntroColoc.add(mutualE);
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - mutualEntroColocation_v2 finished %d0%%", i/(FrequentPair.size()/10)));
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("mutualEntroColocation_v2 executed for %d seconds", (t_end-t_start)/1000));
		return mutualEntroColoc;
	}
	
	
	/**
	 * Calculate mutual information over colocation from the definition:
	 * 		I(x,y) = \sum \sum p(x,y) log (p(x,y) / (p(x)p(y)) )
	 */
	public static LinkedList<Double> mutualEntropyOnColocation_v3() {
		long t_start = System.currentTimeMillis();
		// 1. calculate the individual probability
		HashMap<Integer, HashMap<Long, Double>> user_loc_prob = new HashMap<Integer, HashMap<Long, Double>>();
		for (int i = 0; i < FrequentPair.size(); i++) {
			int[] p = FrequentPair.get(i);
			// get given target locatoin set
			HashSet<Long> locs = FrequentPair_CoLocations.get(i);
			for (int j = 0; j < 2; j++ ) {
				int uid = p[j];
				int cnt = 0;
				for (Record ra : User.allUserSet.get(uid).records) {
					if (locs.contains(ra.locID)) {
						if (user_loc_prob.containsKey(uid)) {
							if (user_loc_prob.get(uid).containsKey(ra.locID)) {
								double f = user_loc_prob.get(uid).get(ra.locID);
								user_loc_prob.get(uid).put(ra.locID, f + 1);
							} else {
								user_loc_prob.get(uid).put(ra.locID, 1.0);
							}
						} else {
							user_loc_prob.put(uid, new HashMap<Long, Double>());
							user_loc_prob.get(uid).put(ra.locID, 1.0);
						}
						cnt ++;
					}
				}
				
				for (Long locid : user_loc_prob.get(uid).keySet()) {
					double freq = user_loc_prob.get(uid).get(locid);
					user_loc_prob.get(uid).put(locid, freq / cnt);
				}
			}
		}
		
		for (int i = 0; i < FrequentPair.size(); i++ ) {
			// 2. calculate the pair frequency
			HashMap<Long, Double> pairLocProb = new HashMap<>();
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			HashSet<Long> locs = FrequentPair_CoLocations.get(i);
			int totalCase = 0;
			for (Record ar : User.allUserSet.get(uaid).records) {
				for (Record br : User.allUserSet.get(ubid).records) {
					// ar and br are same location
					if (locs.contains(ar.locID) && ar.locID == br.locID) {
						if (pairLocProb.containsKey(ar.locID)) {
							double f = pairLocProb.get(ar.locID) + 1;
							pairLocProb.put(ar.locID, f);
							totalCase ++;
						} else {
							pairLocProb.put(ar.locID, 1.0);
							totalCase ++;
						}
					}
				}
			}
			double mutualE = 0;
			for (Long l : pairLocProb.keySet()) {
				// 3. calculate the pair probability
				double f = pairLocProb.get(l);
				double pairProb = f / totalCase;
				// 4. calculate the mutual information
				mutualE += pairProb * Math.log(pairProb / user_loc_prob.get(uaid).get(l) / user_loc_prob.get(ubid).get(l));
			}
			mutualEntroColoc_v3.add(mutualE);
			// monitor the process
			if (i % (FrequentPair.size()/10) == 0)
				System.out.println(String.format("Process - mutualEntroColocation_v3 finished %d0%%", i/(FrequentPair.size()/10)));
		}
		long t_end = System.currentTimeMillis();
		System.out.println(String.format("mutualEntroColocation_v3 executed for %d seconds", (t_end-t_start)/1000));
		return mutualEntroColoc_v3;
	}
	
	
	public static LinkedList<Double> relativeMutualEntropy() {
		for (int i = 0; i < FrequentPair.size(); i++) {
			int uaid = FrequentPair.get(i)[0];
			int ubid = FrequentPair.get(i)[1];
			HashSet<Long> locs = FrequentPair_CoLocations.get(i);
			double entroA = marginalEntropy(uaid, locs);
			double entroB = marginalEntropy(ubid, locs);
			System.out.println(String.format("entro A %g, B %g", entroA, entroB));
			relaMutualEntro.add(mutualEntroColoc.get(i) / (entroA + entroB));
		}
		return relaMutualEntro;
	}
	
	
	/**
	 * Assistant function to write out the results
	 */
	@SuppressWarnings("unused")
	private static void writeThreeMeasures(String filename) {
		try{
			BufferedWriter fout = new BufferedWriter(new FileWriter(filename));
			// output Renyi entropy diversity
			for (double d : renyiDiversity) {
				fout.write(Double.toString(d) + "\t");
			}
			fout.write("\n");
			// output weighted co-occurrence frequency
			for (double d : weightedFreq) {
				fout.write(Double.toString(d) + "\t");
			}
			fout.write("\n");
			// output mutual information
			for (double d : mutualInfo) {
				fout.write(Double.toString(d) + "\t");
			}
			fout.write("\n");
			// output interestingness from PAKDD
			for (double d : interestingness) {
				fout.write(Double.toString(d) + "\t");
			}
			fout.write("\n");
			// output frequency
			for (int i : frequency) {
				fout.write(Integer.toString(i) + "\t");
			}
			fout.write("\n");
			for (double i : mutualEntroColoc) {
				fout.write(Double.toString(i) + "\t");
			}
			fout.write("\n");
			for (double i : mutualEntroColoc_v3) {
				fout.write(Double.toString(i) + "\t");
			}
			fout.write("\n");
			for (double i : relaMutualEntro) {
				fout.write(Double.toString(i) + "\t");
			}
			fout.write("\n");
			fout.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String argv[]) {
		// 1. find frequent user pair
		initializeUsers();
		shareLocationCount();

		// 2. calculate feature one -- Renyi entropy based diversity
		RenyiEntropyDiversity();
//		// 3. calculate feature two -- weighted frequency, and frequency
		weightedFrequency();
		writePairMeasure();
//		// 4. calculate feature three -- mutual information
//		mutualInformation();
//		mutualInformation_v2();
//		// 5. calculate feature four -- interestingness
//		interestingnessPAKDD();
//		// 6. calculate mutual information over colocations
//		mutualEntropyOnColocation();
//		mutualEntropyOnColocation_v2();
//		mutualEntropyOnColocation_v3();
//		relativeMutualEntropy();
		// 6. write the results
//		writeThreeMeasures("feature-vectors-rme.txt");
		
//		writeOutPairColocations();

//		for (int i = 1; i < 10; i += 1)
//			writeLocationEntropy(5000, true, 2);
		
//		evaluateSIGMOD();
	}
	
	public static void evaluateSIGMOD() {
		// initialize top users
		initializeUsers();
		initialTopKPair(5000);
		RenyiEntropyDiversity();
		weightedFrequency();
		writePairMeasure();

	}

}
