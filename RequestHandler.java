import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import org.json.simple.*;
import org.json.simple.parser.*;

public class RequestHandler implements Runnable {
	
    static ConcurrentHashMap<String, JSONObject> d = new ConcurrentHashMap<String, JSONObject>(16, 0.9f, 2);
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
	    while(!s.isClosed()) {
		String inJSON = RequestHandler.readString(new InputStreamReader(s.getInputStream(), "UTF8"), 64);

		if(inJSON.length() == 36)
		    registerReceivingSocket(inJSON); // New receiving clients send their UUIDs, so we add them to a register and use them to transport data back
		else
		    pollSendingSocket(inJSON);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    
    public void registerReceivingSocket(String inJSON) {
	try {
	    sl.put(inJSON, s);
	    synchronized(sl) { sl.notifyAll(); }
	    while(!s.isClosed())
		synchronized(s) { s.wait(); }
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void pollSendingSocket(String inJSON) {
	try {
	    JSONObject j = (JSONObject) p.parse(inJSON);
	    String party = (String) j.get("party");
	    String uuid = j.get("uuid").toString();
	    
	    JSONObject storedJSON = d.put(party, j);
	    if(storedJSON == null || storedJSON.get("uuid").equals(uuid)) { 
		while(d.get(party) == null || d.get(party).get("uuid").equals(uuid))
		    synchronized(d) { d.wait(); }
	    } else {
		storedJSON = d.remove(party);
		synchronized(d) { d.notifyAll(); }
	    }

	    while(sl.get(uuid) == null) // Ensure we have a receiving socket
		synchronized(sl) { sl.wait(); }

	    Socket rs = sl.get(uuid);

	    if(!rs.isClosed())
		rs.getOutputStream().write(storedJSON.toString().getBytes("UTF8"));
	    else
		synchronized(rs) { rs.notifyAll(); }
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    
    public void closeSocket() {
	try {
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
