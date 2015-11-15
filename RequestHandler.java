import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import org.json.simple.*;
import org.json.simple.parser.*;

public class RequestHandler implements Runnable {
	
    static ConcurrentHashMap<String, String> d = new ConcurrentHashMap<String, String>(16, 0.9f, 2);
    static ConcurrentHashMap<String, Socket> sl = new ConcurrentHashMap<String, Socket>(16, 0.9f, 2);
    static ArrayBlockingQueue<RequestHandler> all = new ArrayBlockingQueue<RequestHandler>(32);
    final JSONParser p;
    final Socket s;
    
    public RequestHandler(Socket s) throws InterruptedException {
	try { all.put(this); } catch (Exception e) {}
	this.s = s;
	p = new JSONParser();
    }
	
    public void run() {
	try {
	    String inJSON = RequestHandler.readString(new InputStreamReader(s.getInputStream(), "UTF8"), 64);

	    if(inJSON.length() == 36) { // New receiving clients send their UUIDs, so we add them to a register and use them to transport data back
		System.out.println("UUID Detected: " + inJSON);
		sl.put(inJSON, s);
		return;
	    }

	    System.out.println("JSON Detected: " + inJSON);
	    
	    JSONObject j = (JSONObject) p.parse(inJSON);
	    String party = (String) j.get("party");
	    String uuid = j.get("uuid").toString();
	    
	    String storedJSON = d.put(party, j.toString());
	    if(storedJSON == null) {
		while(d.get(party).equals(j.toString()))
		    synchronized(d) { d.wait(); }
		storedJSON = d.get(party);
		d.remove(party);
	    } else {
		synchronized (d) { d.notifyAll(); }
	    }

	    while(sl.get(uuid) == null) { // Ensure we have a receiving socket
		synchronized(sl) { sl.wait(); }
	    }
	    
	    Socket rs = sl.get(uuid);
	    rs.getOutputStream().write(storedJSON.getBytes("UTF8"));
	    rs.close();
	    System.out.println("Closed auxillary socket" + rs.getPort());
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    try {
		System.out.println("Closing socket " + s.getPort());
		s.close();
	    } catch (Exception e) {
		System.out.println("Error closing a socket!");
	    }
	}      
    }
    
    public void closeSocket() {
	try {
	    System.out.println("Closing sockets...");
	    s.close();
	} catch (Exception e) {
	    e.printStackTrace();
	} 
    }
    
    public static String readString(Reader ir, int buffersize) throws IOException {
	StringBuilder inJSONBuilder = new StringBuilder();
	char[] buffer = new char[buffersize];
	do {
	    int i = ir.read(buffer, 0, buffersize);
	    if(i != -1)
		inJSONBuilder.append(buffer, 0, i);
	} while (ir.ready());
	return inJSONBuilder.toString();
    }
}
