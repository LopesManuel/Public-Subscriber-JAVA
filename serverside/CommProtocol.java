import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Scanner;

public class CommProtocol {
    public static final int WAITING = 0;
    public static final int WATCHING = 1;
    public static final int STREAMING = 2;
  
    public int state = WAITING;
    public String url = null;
    
	public String localPath = null; 
	public String serverPath = null; 
    
	private String[] commands = { "list", "subscribe", "unsubscribe", "publish", "unpublish", "help", "exit" };
    private String help =  "1- subscribe <stream> - subscribes available stream and starts receiving data\n"
    						+ "2- unsubscribe - unsubscribes current stream\n" 
    						+ "3- list - lists all the streams available\n"
    						+ "4- publish - starts streaming\n"
    						+ "5- unpublish - stops streaming\n";
    						
	private Scanner stdin;
    
    
	public String processInput(String input) throws IOException {

		String cmdTemp = null;
		String output = null;
        String stream_id = null;
        
 		if(input == null || input.equals(""))
         	return "";
 		
		stdin = new Scanner(input);
		cmdTemp = stdin.next();
		if(stdin.hasNext())
			stream_id = stdin.next();
		
		boolean isCommand = isCommand(cmdTemp);

        if(!isCommand && state != STREAMING){
        	output= "Type \"help\" to view the list of commands";
        	return output;
        }
        if(isEqual(commands[commands.length-1], cmdTemp)){
        	state = WAITING;
    		return "STP";
    	}
        if(state != STREAMING){
	        if(isEqual(commands[commands.length-2], cmdTemp)){
	        	output= help;
	        	return output;
	        }
	        //list
	    	if(isEqual(commands[0], cmdTemp)){
	    		return "LST";
	    	}
	        if (state == WAITING) {
		        //subscribe
		    	if(isEqual(commands[1], cmdTemp) ){
			    	state = WATCHING;
		    		return "SUB " + stream_id;
		    	}
		    	//publish
		    	if(isEqual(commands[3], cmdTemp) ){
			    	state = STREAMING;
		    		return "PUB " + stream_id;	
		    	}
		    	//unpublish
		    	if(isEqual(commands[4], cmdTemp) ){
		    		return "You need to be a publisher to be able to unpublish";
		    	}
		        //unsubscribe
		    	if(isEqual(commands[2], cmdTemp) ){
		    		return "You need to be a subscriber to be able to unsubscribe";	
		    	}	
	    	}
	        else{
		        //unsubscribe
		    	if(isEqual(commands[2], cmdTemp) ){
			    	state = WAITING;
		    		return "UNS " + stream_id;
		    	}
	        }
        }
        else{		    	
        	//unpublish
	    	if(isEqual(commands[4], cmdTemp) ){
		    	state = WAITING;
	    		return "UNP " + stream_id;	
	    	}
        	return "FWD " + input;
        }
        return output;
	}
	private boolean isCommand(String cmd){
		if(cmd != null){
			int c = commands.length;
			for(int i=0; i < c; i++ ){			
				//equals raises problems because of the string his the direct conversion
				//from the byte array, and it has size = CHUNK_SIZE
				if(commands[i].length() == indexOfDifference(commands[i], cmd)) 
					return true;
			}
		}
		return false;
	}
	public boolean isEqual(String s1, String s2){
		if(s1.length() == indexOfDifference(s1, s2)) 
			return true;
		return false;
	}
	public static int indexOfDifference(CharSequence cs1, CharSequence cs2) {
	    if (cs1 == cs2) {
	        return cs1.length();
	    }
	    if (cs1 == null || cs2 == null) {
	        return 0;
	    }
	    int i;
	    for (i = 0; i < cs1.length() && i < cs2.length(); ++i) {
	        if (cs1.charAt(i) != cs2.charAt(i)) {
	            break;
	        }
	    }
	    if (i < cs2.length() || i < cs1.length()) {
	        return i;
	    }
	    return cs1.length();
	}
}
