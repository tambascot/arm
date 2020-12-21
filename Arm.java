import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

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

// import com.sun.xml.internal.bind.v2.schemagen.xmlschema.List;

// import jdk.internal.org.xml.sax.InputSource;
// import jdk.internal.org.xml.sax.SAXException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.client.googleapis.services.json.*;
// import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.*;

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
	private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_LABELS);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "A.R.M.";

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
    			
                org.jdom2.Element firstRec 	= recordsMap.get(firstSeq);
                org.jdom2.Element lastRec	= recordsMap.get(lastSeq);
                
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
	
    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @param JsonCredentialsFile The JSON file with Google App Console Credentials
     * @param Tokens_DIRECTORY_PATH The path to the directory containing authentication tokens.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, 
    		File JsonCredentialsFile, String TOKENS_DIRECTORY_PATH) throws IOException {
        // Load client secrets.
    	// C:\Users\production\.SnapshotFiles\SoftgoodSnapshot2Credentials.json
    	InputStream in = new FileInputStream(JsonCredentialsFile);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(9001).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    
    /**
     * Create a MimeMessage using the parameters provided.
     * copies from https://developers.google.com/gmail/api/guides/sending
     *
     * @param to email address of the receiver
     * @param from email address of the sender, the mailbox account
     * @param subject subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     * @throws javax.mail.MessagingException 
     * @throws AddressException 
     */
    public static MimeMessage createEmail(String to,
                                          String from,
                                          String subject,
                                          String bodyText)
            throws MessagingException, AddressException, javax.mail.MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(to));
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }
    
    /**
     * Create a message from an email.
     * copied from https://developers.google.com/gmail/api/guides/sending
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     * @throws javax.mail.MessagingException 
     */
    public static Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException, javax.mail.MessagingException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }
    
    /**
     * Send an email from the user's mailbox to its recipient.
     * copied from https://developers.google.com/gmail/api/guides/sending
     *
     * @param service Authorized Gmail API instance.
     * @param userId User's email address. The special value "me"
     * can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     * @return The sent message
     * @throws MessagingException
     * @throws IOException
     * @throws javax.mail.MessagingException 
     */
    public static Message sendMessage(Gmail service,
                                      String userId,
                                      MimeMessage emailContent)
            throws MessagingException, IOException, javax.mail.MessagingException {
        Message message = createMessageWithEmail(emailContent);
        message = service.users().messages().send(userId, message).execute();

        System.out.println("Message id: " + message.getId());
        System.out.println(message.toPrettyString());
        return message;
    }
	
	public static void main(String[] args) {
		
		/*
		 * Declare variables
		 */
		
		File logDirectory					 = null;
		Map<String, String> updateLogReports = null;
		String recipientAddress 			 = null;
		String tokensFilePath	 		 	 = null;
	    File jsonCredentialsFile			 = null;
		
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
		
		Option emailReport = new Option("e", "email", true, 
				"Specify an email address to send the report to.");
		options.addOption(emailReport);
		
		Option tokenDirPath = new Option("t", "token", true, 
				"Specify a directory for authentication tokens (other than default). Required for sending email.");
		options.addOption(tokenDirPath);
	      
		Option jsonFile = new Option("j", "json", true, 
				"Specify a JSON file with Google Application authentication data (other than default)."
					+ " Required for sending email.");
		options.addOption(jsonFile);
		
		/*
		Option databaseFile = new Option("d", "database", true, 
				"Specify a database file to run reports on.");
		options.addOption(databaseFile);
		*/

	
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
			
			if (cmd.hasOption("e")) {
				
				/*
				 * Sending an email requires we also have a JSON file and an auth token to work with. If those
				 * were not specified on the command line, print an error and exit with error status. 
				 */
				if (!cmd.hasOption("j") && !cmd.hasOption("t")) {
					System.err.println("Error, JSON file and token directory must be specified to send email");
					System.exit(1);
				}
				
				recipientAddress 	= cmd.getOptionValue("e");
				tokensFilePath	 	= cmd.getOptionValue("t");
			    jsonCredentialsFile	= new File(cmd.getOptionValue("j"));
				
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
		 * Send an email to designated user incorporating the results of all reports into the message body.
		 */
	      
	      if (recipientAddress != null) {
	    	  
	    	  String bodyText = "";
	    	  
	    	  try {
	    		  final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		          Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, jsonCredentialsFile, 
		        		  tokensFilePath))
		                  .setApplicationName(APPLICATION_NAME)
		                  .build();
		    	  updateLogReports.forEach((k, v) -> bodyText.concat(k + ": " + v + "\n"));
	    		  MimeMessage message = createEmail(recipientAddress, "me", "Log Report", bodyText);
	    		  Message emailMsg = createMessageWithEmail(message);
	    		  sendMessage(service, "me", message);
	    		  
	    		  
	    	  } catch (Exception e) {
	    		  e.printStackTrace();
	    	  }
	    	  
	      }
	      
	      if (updateLogReports != null) {
	    	  
	    	  // Uncomment for debugging log report.
	    	  updateLogReports.forEach((k, v) -> System.out.println((k + ": " + v)));
	      }
	}

}