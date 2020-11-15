import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.List;

import jdk.internal.org.xml.sax.InputSource;
import jdk.internal.org.xml.sax.SAXException;

/**
 * A.R.M. - Asst. Rentals Manager
 * 
 * A class to generate reports on logs and databases to make the life of the Gateway Rentals Manager 
 *   / Asst. Production Manager easier. 
 * 
 * By Tony Tambasco <tambascot@yahoo.com>
 * 
 * This is Free Software published under the terms of the Gnu Public Liscence (GPL) v.2
 * 
 * @author Tony Tambasco
 * @author tambascot@yahoo.com
 *
 */

public class Arm {
	
	private static final String VERSION = "1.0.0.0";
	
	static FilenameFilter logFilter = new FilenameFilter() {
        @Override
        public boolean accept(File f, String name) {
            return name.endsWith(".log");
        }
    };
    
	
	@SuppressWarnings("deprecation")
	private static Map<String, String> updateLogReport(File logDirectory) {
		
		Map<String, String> logReport = new HashMap<String, String>();
		
		String filepaths[] = logDirectory.list(logFilter);
		
		for (String logfile : filepaths) {
			
			String logPath = logDirectory.getAbsolutePath() 
					+ "\\" + logfile;
			
			System.out.println(logPath);
			
			try{
                // Open and parse the file into an HOH
                SAXBuilder parser = new SAXBuilder();
                
                parser.setEntityResolver(new EntityResolver() {
               	 @Override
               	 public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) throws IOException {
               	  if (systemId.contains("logger.dtd")) {
               	   return new org.xml.sax.InputSource(new StringReader(""));
               	  } else {
               	   return null;
               	  }
               	 }
               	});
                
                // Create a document given the URI to the file
                Document doc = parser.build(logPath);

                // Get the root node of the document
                org.jdom2.Element root = doc.getRootElement();

                java.util.List<org.jdom2.Element> records = root.getChildren("record");
                
                /* We need to make sure we're getting the records in order, and to do that we
                 * need to create a sorted map, interate over the list of records, extract the sequence
                 * number from each record, and then add the records to the sorted map using the 
                 * sequence number as a key. The first sequence should always be 0. If the current
                 * sequence is greater than the last sequence, replace last sequence with current
                 * sequence. 
                 * 
                 */
                HashMap<Integer, org.jdom2.Element> recordsMap = new HashMap<Integer, org.jdom2.Element>();
                
                int firstSeq = 0;
                int lastSeq  = firstSeq;
                		
                
                for (int i = 0; i < records.size(); i++) {
                    org.jdom2.Element record = records.get(i);
 
                    int currSeq = Integer.parseInt(record.getChild("sequence").getValue());
                    
                    // System.out.println(record.getChild("sequence").getValue());
                    
                    if (currSeq > lastSeq) {
                    	lastSeq = currSeq;
                    }
                    
                    recordsMap.put(currSeq, record);
                }
                
                /*
                 * If execution went as planned, the message of the first entry will being "Beginning update on" and the
                 * message of the third entry will begin "Update complete at". If the first entry and the last entry say
                 * anything other than those messages, there was a critical problem, and we should report that first. If there
                 * are more than three entries, there may be informational messages or non-critical problems to report.
                 */
                
                /*
                 * Was there a critical problem?
                 */
    			
                org.jdom2.Element firstRec = recordsMap.get(firstSeq);
                org.jdom2.Element lastRec	 = recordsMap.get(lastSeq);
                
                String firstMess = firstRec.getChild("message").getValue();
                String lastMess  = lastRec.getChild("message").getValue();
                
                if (! firstMess.startsWith("Beginning update on")
                		|| ! lastMess.startsWith("Update complete at")) {
                	logReport.put(logfile, "Critical error! Please inspect log file.");
                }
                      
                /*
                 * Was there a anything worth looking at?
                 */
                
                else if (lastSeq > 4) {
                	logReport.put(logfile, "Update successfull, but with errors. Please check log.");
                }
                
                /*
                 * If all is well, report that all is well.
                 */
                
                else {
                	logReport.put(logfile, "Update successfull");
                }
			
			}
			catch (IOException e) {
                e.printStackTrace();
            }
            catch (JDOMException j) { 
                j.printStackTrace();
            }
		
		
		} // end for each log file found
		
		return logReport;
	}
	
	private boolean upcomingRentalsReport(File rentalsDatabase) {
		
		return true;
	}
	
	private boolean missingSoftgoodsReport(File softgoodsDatabase) {
		
		return true;
	}
	
	public static void main(String[] args) {
		
		/*
		 * Declare variables
		 */
		
		File logDirectory = null;
		Map<String, String> updateLogReports = null;
		
		/*
		 * Process command line options. 
		 */
		
		Options options = new Options();
		
		Option about = new Option("a", "about", false, "Print about SoftgoodSnapshot and exit.");
		options.addOption(about);
		
		Option help = new Option("h", "help", false, "Print this help menu and exit.");
		options.addOption(help);  
		  
		Option logDirPath = new Option("l", "logs", true, 
				"Specify a log directory.");
		options.addOption(logDirPath);
		  
		Option databaseFile = new Option("d", "database", true, 
				"Specify a database file to run reports on.");
		options.addOption(databaseFile);

	
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;
	
		try {
	    	  
			// If there are no arguments, print help and exit
			if (args.length == 0) {
				System.out.println("A.R.M. Help\n");
				formatter.printHelp("arm", options );
				System.exit(0);
			}
	    	  
			cmd = parser.parse(options, args);
	    	  
			// Begin parsing any options that will cause the program to exit.
	    	  
			if (cmd.hasOption("a")) {
				System.out.println("A.R.M. -- generate reports to assist the rentals manager."
						+ "\n(c) 2020 by Tony Tambasco (tambascot@yahoo.com)."
		    	  		+ "\nVersion: " + VERSION
		    	  		+ "\nTry --help for more options.");
				System.exit(0);
			}
		      
			else if (cmd.hasOption("h")) {
				System.out.println("A.R.M. Help\n");
				formatter.printHelp("usage: java -jar arm.jar", options );
				System.exit(0);
			}
		      
			// Parse non mutually exclusive arguments below
		      	      
			if (cmd.hasOption("l")) {
				logDirectory = new File(cmd.getOptionValue("l"));
			}
		      
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("utility-name", options);
			System.exit(1);
		}
	      
		
		/*
		 * Test all input files / directories to make sure they exist and are readable.
		 */
		
	      if (logDirectory != null) {
	    	  
	    	  if (logDirectory.canRead()) {
	    		  updateLogReports = updateLogReport(logDirectory);
	    	  }
	      }
		
		/*
		 * Send an email to production incorporating the results of all reports into the message body.
		 */
	      
	      if (updateLogReports != null) {
	    	  
	    	  // Uncomment for debugging log report.
	    	  updateLogReports.forEach((k, v) -> System.out.println((k + ": " + v)));
	      }
	}

}
