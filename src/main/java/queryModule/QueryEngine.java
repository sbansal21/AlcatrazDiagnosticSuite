package queryModule;

import java.io.*;
import java.text.*;
import java.util.*;

import driver.SQLiteManager;

/**
 * Pulls queried data from MongoDB and compares key values.
 * 
 * @author ActianceEngInterns
 * @version 1.3
 */
public class QueryEngine {

	// each ArrayList is a single query pair {left filter, right filter}
	// the Queue then represents a series of queries to be executed
	private Queue<ArrayList<Map<String, String>>> queuedQueries = new LinkedList<>();

	// Set of paths excluded from query--generated in excludeQuery()
	private Set<String> exclusions = new HashSet<>();

	/*
	 * String[]: CSV row, formatted {file, key, value, file, key, value, key diff, value diff}
	 * 
	 * ArrayList<String[]>: a single SQL_TABLE containing the entirety of a comparison between queries
	 * 
	 * ArrayList<ArrayList<String>>: multiple tables, necessary due to how internal queries generate
	 * several tables for each comparison between fabrics/nodes
	 */
	private LinkedList<LinkedList<String[]>> tables = new LinkedList<>();

	private Set<String> filenames = new TreeSet<>();
	private Map<String, Integer> discrepancies = new HashMap<>();
	
	private String[] genericPath = SQLiteManager.genericPath;
	private String[] reversePath = SQLiteManager.reversePath;
	private final String SQL_TABLE = SQLiteManager.getTable();

	/**
	 * Constructor.
	 */
	public QueryEngine() {
		discrepancies.put("key", 0);
		discrepancies.put("value", 0);
		discrepancies.put("ignored", 0);
	}

	/**
	 * Getter method for SQL_TABLE.
	 * 
	 * @return a 2D representation of CSV as a series of tables, with each SQL_TABLE representing a
	 *         single comparison
	 */
	public LinkedList<LinkedList<String[]>> getTables() {
		return tables;
	}

	/**
	 * Getter method for the discrepancy statistics of a query.
	 * 
	 * @return the discrepancy statistics as a Map where the entry "key" corresponds to the total
	 *         number of differences in the keys of a query, the entry "value" corresponds to the
	 *         total number of differences in the values of a query, and the entry "ignored"
	 *         corresponds to the total number of properties that were ignored by the QueryEngine
	 */
	public Map<String, Integer> getDiscrepancies() {
		return discrepancies;
	}

	/**
	 * Takes in a single path input and generates a series of queries between the subdirectories of
	 * the specified path.
	 * <dl>
	 * <dt>example path parameters:
	 * <dd>dev1/fabric2
	 * </dl>
	 * <dl>
	 * <dt>example queries:
	 * <dd>dev1/fabric2/node1
	 * <dd>dev1/fabric2/node2
	 * <dd>dev1/fabric2/node1
	 * <dd>dev1/fabric2/node3
	 * <dd>dev1/fabric2/node2
	 * <dd>dev1/fabric2/node3
	 * </dl>
	 * 
	 * @param path
	 *            the path containing the subdirectories being compared against each other
	 * @return a String representing the status of the query: null if successful, else error message
	 */
	public String generateInternalQueries(String path) {
		
		// generates filter
		Map<String, String> filter = SQLiteManager.generatePathFilter(path);

		// rebuilds path (in case of wildcard extensions)
		String loc = "";
		for (int j = 0; j < genericPath.length; j++) {
			if (filter.get(genericPath[j]) != null) {
				loc += filter.get(genericPath[j]) + "/";
			} else if (j < path.split("/").length) {
				loc += "*/";
			}
		}

		// retrieves all the subdirectories of the path
		Iterator<String> iter = null;
		for (int i = 0; i < reversePath.length; i++) {
			if (filter.get(reversePath[i]) != null) {
				iter = SQLiteManager.getDistinct(reversePath[i - 1], filter).iterator();
				break;
			}
		}
		
		// defaults to large-scale environment comparisons (e.g. in the case of path "*")
		if (iter == null) {
			iter = SQLiteManager.getDistinct("environment", filter).iterator();
			loc = "";
		}

		// adds all paths in the level directly below to a List
		ArrayList<String> subdirs = new ArrayList<>();
		while (iter.hasNext()) {
			String subdir = loc + iter.next();
			
			// if an extension wildcard was specified, rebuilds complete path
			if (filter.get("extension") != null) {
				for (int i = subdir.split("/").length - 1; i < genericPath.length; i++) {
					subdir += "/*";
				}
				subdir += "." + filter.get("extension");
			}
			subdirs.add(subdir);
		}

		if (subdirs.isEmpty()) {
			return "[ERROR] No matching path found.";
		}
		if (subdirs.size() < 2) {
			return "[ERROR] Directory must contain at least 2 files or subdirectories."
					+ "\n        Only matching subdirectory found: " + subdirs.get(0);
		}

		// query each unique pair of files within List
		String status = "";
		for (int i = 0; i < subdirs.size() - 1; i++) {
			for (int j = i + 1; j < subdirs.size(); j++) {
				status += addQuery(subdirs.get(i), subdirs.get(j));
			}
		}
		return status.length() == 0 ? null : status;
	}

	/**
	 * Adds path inputs to the internal queuedQueries List.
	 * 
	 * @param pathL
	 *            the first path being compared
	 * @param pathR
	 *            the other path being compared
	 * 
	 * @return a String containing any filters throwing exceptions (empty if none)
	 */
	public ArrayList<Map<String, String>> addQuery(String pathL, String pathR) {

		ArrayList<Map<String, String>> filters = new ArrayList<>();
		filters.add(SQLiteManager.generatePathFilter(pathL));
		filters.add(SQLiteManager.generatePathFilter(pathR));
		ArrayList<Map<String, String>> added = new ArrayList<>();
		try {

			// adds query filters to queuedQueries
			added.add(filters.get(0));
			added.add(filters.get(1));
			queuedQueries.add(filters);

			// adds lowest-level path difference to CSV header
			String[] splitL = pathL.split("/");
			String[] splitR = pathR.split("/");
			for (int i = splitL.length - 1; i >= 0; i--) {
				if (!splitL[i].equals(splitR[i])) {
					filenames.add(splitL[i]);
					filenames.add(splitR[i]);
					break;
				}
			}

		} catch (Exception e) {
			for (Map<String, String> filter : added) {
				filter.put("error", "true");
			}
		}
		return added;
	}

	/**
	 * Excludes queried files with certain attributes from being compared.
	 * 
	 * @param path
	 *            the path of the file being blocked
	 * @return a String containing all exclusion filters (empty if none)
	 */
	public Map<String, String> exclude(String path) {

		// generates filter for exclusion
		Map<String, String> filter = SQLiteManager.generatePathFilter(path);

		// adds all exclusions to a Set to be cross-referenced during comparison
		exclusions.addAll(SQLiteManager.getDistinct("path", filter));

		return filter;
	}

	/**
	 * Clears the internal queuedQueries and exclusions Lists.
	 */
	public void clearQuery() {
		queuedQueries.clear();
		exclusions.clear();
		for (Map.Entry<String, Integer> entry : discrepancies.entrySet()) {
			discrepancies.put(entry.getKey(), 0);
		}
	}

	/**
	 * Retrieves filtered files from the MongoDB database, excludes files as appropriate, compares
	 * the remaining queried files, and adds the results to a CSV file.
	 * 
	 * @return a String detailing the results of the operation
	 */
	public String run() {

		// if single query, sets column filenames to query comparison
		// else, in case of internal query, determines parent directory
		String left = "root";
		String right = "root";
		if (queuedQueries.size() == 1) {
			Map<String, String> comp1 = queuedQueries.peek().get(0);
			Map<String, String> comp2 = queuedQueries.peek().get(1);

			for (String key : genericPath) {
				String val1 = comp1.get(key);
				String val2 = comp2.get(key);
				if (val1 != null && val2 != null && !val1.equals(val2)) {
					left = val1;
					right = val2;
				}
			}
		} else {
			Map<String, String> query = queuedQueries.peek().get(0);
			String stop = "";
			for (int i = 0; i < reversePath.length; i++) {
				if (query.containsKey(reversePath[i])) {
					stop = reversePath[i];
					break;
				}
			}
			if (!stop.equals("environment")) {
				int i = 0;
				left = "";
				while (i < genericPath.length && !genericPath[i].equals(stop)) {
					left += query.get(genericPath[i]) + "/";
					i++;
				}
				right = left;
			}
		}

		// adds header for full CSV table
		String[] header = { left, "left key", "left value", right, "right key", "right value", "key status",
				"value status" };
		LinkedList<String[]> tableHeader = new LinkedList<>();
		tableHeader.add(header);
		tables.add(tableHeader);
		
		// initializes statistic tracking for comparison
		int queried = 0;
		int excluded = 0;

		// adds properties matching both sides of query
		while (queuedQueries.peek() != null) {
			ArrayList<Map<String, String>> query = queuedQueries.poll();

			// creates Document lists as specified in the query
			// Maps used to rapidly hash keys and corresponding properties for constant lookup
			Map<String, Map<String, String>> propsL = new LinkedHashMap<>();
			Map<String, Map<String, String>> propsR = new LinkedHashMap<>();
			
			String sql;
			Iterator<Map<String, String>> iter;

			// finds all unblocked properties on left side of query
			sql = "SELECT * FROM " + SQL_TABLE + SQLiteManager.generateSQLFilter(query.get(0), null);
			iter = SQLiteManager.select(sql).iterator();
			while (iter.hasNext()) {
				Map<String, String> property = iter.next();
				if (exclusions.contains(property.get("path"))) {
					excluded++;
				} else {
					propsL.put(property.get("key"), property);
				}
				queried++;
			}

			// finds all unblocked properties on right side of query
			sql = "SELECT * FROM " + SQL_TABLE + SQLiteManager.generateSQLFilter(query.get(1), null);
			iter = SQLiteManager.select(sql).iterator();
			while (iter.hasNext()) {
				Map<String, String> property = iter.next();
				if (exclusions.contains(property.get("path"))) {
					excluded++;
				} else {
					propsR.put(property.get("key"), property);
				}
				queried++;
			}

			// compares sides of a query and adds to output table
			tables.add(compare(propsL, propsR));

		}

		if (queried == 0) {
			return "[ERROR] No matching properties found.";
		}
		return "Found " + queried + " properties and excluded " + excluded + " properties matching query.";
	}

	/**
	 * Compares Documents and adds the comparison outcomes to the SQL_TABLE.
	 * 
	 * @param propsL
	 *            a List of Documents representing every property in the left side of the query
	 * @param propsR
	 *            a List of Documents representing every property in the right side of the query
	 * @return the SQL_TABLE as an ArrayList of String[] containing the entirety of a comparison between
	 *         queries, with each String[] representing a CSV row
	 */
	private LinkedList<String[]> compare(Map<String, Map<String, String>> propsL, Map<String, Map<String, String>> propsR) {

		// generates key set
		Set<String> keyAmalgam = new LinkedHashSet<>();
		keyAmalgam.addAll(propsL.keySet());
		keyAmalgam.addAll(propsR.keySet());

		// sets up row information
		LinkedList<String[]> table = new LinkedList<>();

		for (String key : keyAmalgam) {

			// finds appropriate property from the keyset
			Map<String, String> propL = propsL.get(key);
			Map<String, String> propR = propsR.get(key);

			// copies property values to Strings
			String pathL = propL != null ? propL.get("path") : "";
			String pathR = propR != null ? propR.get("path") : "";
			String keyL = propL != null ? key : "";
			String keyR = propR != null ? key : "";
			String valueL = propL != null ? propL.get("value") : "";
			String valueR = propR != null ? propR.get("value") : "";

			// compares and generates diff report
			String keyStatus, valueStatus;
			if (propL == null) {
				keyStatus = valueStatus = "missing in left";
				discrepancies.put("key", discrepancies.get("key") + 1);
			} else if (propR == null) {
				keyStatus = valueStatus = "missing in right";
				discrepancies.put("key", discrepancies.get("key") + 1);
			} else if (propL.get("ignore").equals("true") || propL.get("ignore").equals("true")) {
				keyStatus = valueStatus = "ignored";
				discrepancies.put("ignored", discrepancies.get("ignored") + 1);
			} else if (propL.get("key").equals(propL.get("key")) && !valueL.equals(valueR)) {
				keyStatus = "same";
				valueStatus = "different";
				discrepancies.put("value", discrepancies.get("value") + 1);
			} else if (propL.get("key").equals(propL.get("key"))) {
				keyStatus = valueStatus = "same";
			} else {
				keyStatus = valueStatus = "";
			}
			String[] row = { pathL, keyL, valueL, pathR, keyR, valueR, keyStatus, valueStatus };
			table.add(row);
		}
		return table;
	}

	/**
	 * Writes stored data to a CSV file with a user-specified name and directory.
	 * 
	 * @param filename
	 *            the user-specified filename
	 * @param directory
	 *            the user-specified directory
	 * @return a String detailing the results of the operation
	 */
	public String writeToCSV(String filename, String directory) {
		if (tables.size() <= 1) {
			return "[ERROR] Unable to write CSV because no queries were executed.";
		}

		try {
			String path = directory + "/" + filename + ".csv";
			BufferedWriter writer = new BufferedWriter(new FileWriter(path, true));
			for (LinkedList<String[]> table : tables) {
				for (String[] arr : table) {
					for (String str : arr) {
						if (str.equals("null")) {
							writer.write("\"\"" + ",");
						} else {
							writer.write("\"" + str.replace("\"", "'") + "\"" + ",");
						}
					}
					writer.write("\n");
				}
			}
			writer.close();
			return null;
		} catch (IOException e) {
			return "[ERROR] Unable to write to CSV.";
		}
	}

	/**
	 * Creates a default name for the CSV file based on the lowest-level metadata provided in the
	 * query.
	 * 
	 * @return the default CSV name
	 */
	public String getDefaultName() {
		String defaultName = "lighthouse-report";
		DateFormat nameFormat = new SimpleDateFormat("_yyyy-MM-dd_HH.mm.ss");
		Date date = new Date();
		defaultName += nameFormat.format(date);
		for (String filename : filenames) {
			if (defaultName.length() < 100) {
				defaultName += "_" + filename;
			}
		}
		return defaultName;
	}

}
