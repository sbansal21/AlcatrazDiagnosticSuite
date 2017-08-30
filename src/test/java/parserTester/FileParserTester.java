package parserTester;

import java.io.*;

import parser.*;

/**
 * Tests the Parser.
 * 
 * @author ActianceEngInterns
 * @version 1.1
 */
public class FileParserTester {
	
	/**
	 * Runs the tester.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		String dir = System.getProperty("user.home") + "/workspace/diagnosticSuite/root/";
		String path = dir + "jeremy/storm/common/server.properties";
		FileParser reader = new FileParser(new File(dir), new File(path));
		reader.parseFile();
		System.out.println(reader.getData());
	}
	
}