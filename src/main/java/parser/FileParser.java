package parser;

import java.io.*;

/**
 * Takes input file and parses according to file type.
 * 
 * @author ActianceEngInterns
 * @version 1.0
 */
public class FileParser {
	private File input;
	private AbstractParser data;
	private String errorDescription;

	/**
	 * Constructor. Initializes internal File and Standardizer variables.
	 * 
	 * @param f
	 *            input File being read
	 */
	public FileParser(File f) {
		this.input = f;
		instantiateParser();
	}

	/**
	 * Determines the type of the File and instantiates the parser accordingly.
	 */
	public void instantiateParser() {
		try {
			String filename = input.getAbsolutePath();
			String fileType = filename.substring(filename.lastIndexOf('.') + 1);
			if (fileType.equalsIgnoreCase("config") || fileType.equalsIgnoreCase("conf")) {
				data = new ParseConf();
			} else if (fileType.equalsIgnoreCase("yaml") || fileType.equalsIgnoreCase("yml")) {
				data = new ParseYaml();
			} else if (fileType.equalsIgnoreCase("properties")) {
				data = new ParseProp();
			} else if (input.getName().equalsIgnoreCase("hosts")) {
				data = new ParseHost();
			} else {
				errorDescription = "File " + filename + " was not added because file type " + fileType
						+ " is currently unsupported.\n";
				data = null;
			}
			if (data != null) {
				data.setPath(filename);
			}
		} catch (Exception e) {
			errorDescription = "\n" +  e.getMessage();
			data = null;
		}
	}

	/**
	 * Adds File data to standardized ArrayLists.
	 */
	public int parseFile() {
		if (data != null) {
			data.standardize(input);
			return 0;
		}
		return 1;
	}

	/**
	 * Returns the File-specific parser.
	 * 
	 * @return the File-specific parser
	 */
	public AbstractParser getData() {
		if (data != null) {
			return data;
		}	 
		return null;
	}

	/**
	 * Getter method for error description.
	 * 
	 * @return the error description
	 */
	public String getErrorDescription() {
		return errorDescription;
	}

	/**
	 * Clears the standardized ArrayLists.
	 */
	public void clearData() {
		if (data != null) {
			data.clear();
		}
	}
}
