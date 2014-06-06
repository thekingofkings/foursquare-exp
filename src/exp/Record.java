package exp;

import java.text.ParseException;
import java.text.SimpleDateFormat;


/***
 * Class Record
 * @author Hongjian
 * 
 * represents one check-in record with the following format:
 * 	user id, latitude, longitude, time, location id
 * 	0,37.000,-122.000,2010-04-14 04:32:30,0
 *
 */

public class Record implements Comparable<Record> {
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	int userID;
	String time;
	long timestamp;
	double latitude;
	double longitude;
	long locID;
	
	public Record(String line) {
		String[] ls = line.split(",");
		userID = Integer.parseInt(ls[0]);
		latitude = Double.parseDouble(ls[1]);
		longitude = Double.parseDouble(ls[2]);
		time = ls[3];
		try {
			timestamp = sdf.parse(time).getTime() / 1000;
		} catch (ParseException e) {
			System.out.println(ls[3] + "\n" + e.getMessage());
		}
		locID = Integer.parseInt(ls[4]);
	}
	
	
	/**
	 * Calculate the distance to another record
	 * @param o -- another record
	 * @return -- the distance between two records in km
	 */
	public double distanceTo(Record o) {
		double d2r = (Math.PI/180);
		double distance = 0;
		
		double longiE = o.longitude;
		double latiE = o.latitude;
		try {
			double dlong = (longiE - longitude) * d2r;
			double dlati = (latiE - latitude) * d2r;
			double a = Math.pow(Math.sin(dlati/2.0), 2)
					+ Math.cos(latitude * d2r)
					* Math.cos(latiE * d2r)
					* Math.pow(Math.sin(dlong / 2.0), 2);
			double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
			distance = 6367 * c;
			
		} catch (Exception e) {
			e.printStackTrace();	
		}
		return distance;
	}

	@Override
	public int compareTo(Record o) {
		return (int) (this.timestamp - o.timestamp);
	}
	
	@Override
	public String toString() {
		return String.format("%d,%.6f,%.6f,%s,%d", userID, latitude, longitude, time, locID);
	}
	
	
	public String GPS() {
		return String.format("%.3f%.3f", latitude, longitude);
	}
	

	public static void main(String[] args) {
		// test purpose
		String test = "0,37.806167,-122.450135,2010-04-14 04:32:30,0";
		Record r = new Record(test);
		System.out.println(r);
	}


}
