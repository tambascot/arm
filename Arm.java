import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.w3c.dom.Element;

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.List;

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
	
	static FilenameFilter logFilter = new FilenameFilter() {
        @Override
        public boolean accept(File f, String name) {
            return name.endsWith(".log");
        }
    };
    
	
	private static Map<String, String> updateLogReport(File logDirectory) {
		
		Map<String, String> logReport = new HashMap<String, String>();
		
		String filepaths[] = logDirectory.list(logFilter);
		
		for (String logfile : filepaths) {
			
			try{
                // Open and parse the file into an HOH
                SAXBuilder parser = new SAXBuilder();
                
                // Create a document given the URI to the file
                Document doc = parser.build(logfile);

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
                HashMap<Integer, Element> recordsMap = new HashMap<Integer, Element>();
                
                int firstSeq = 0;
                int lastSeq  = firstSeq;
                		
                
                for (int i = 0; i < records.size(); i++) {
                    Element record = (Element) records.get(i);
                    
                    int currSeq = Integer.parseInt(record.getElementsByTagName("sequence").toString());
                    
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
    			
                Element firstRec = recordsMap.get(firstSeq);
                Element lastRec	 = recordsMap.get(lastSeq);
                
                String firstMess = firstRec.getElementsByTagName("message").toString();
                String lastMess  = lastRec.getElementsByTagName("message").toString();
                
                if (! firstMess.startsWith("Beginning update on")
                		&& ! lastMess.startsWith("Update complete at")) {
                	logReport.put(logfile, "Critical error! Please inspect log file.");
                }
                      
                /*
                 * Was there a anything worth looking at?
                 */
                
                else if (lastSeq > 2) {
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
		
		File directory = new File("C:\\\\Users\\production\\.SnapshotFiles");
		
		Map<String, String> updateLogReports = updateLogReport(directory);
		
		updateLogReports.forEach((k, v) -> System.out.println((k + ":" + v)));
	}

}
