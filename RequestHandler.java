import java.util.concurrent.*;
import java.lang.UnsupportedOperationException;
import java.lang.StringBuilder;
import java.net.Socket;
import java.net.SocketException;
import java.io.IOException;
import java.io.Reader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
	    String inJSONRaw = RequestHandler.readString(new InputStreamReader(s.getInputStream(), "UTF8"), 64);
	    JSONObject inJSON = (JSONObject) p.parse(inJSONRaw);
	    while(!s.isClosed()) {
		if(inJSON.get("mode").equals("sendparty")) {
		    pollSendingSocketParty(inJSON);
		    inJSONRaw = RequestHandler.readString(new InputStreamReader(s.getInputStream(), "UTF8"), 64);
		    inJSON = (JSONObject) p.parse(inJSONRaw);
		} else if(inJSON.get("mode").equals("receiveparty")) {
		    registerReceivingSocketParty(inJSON);
		} else if(inJSON.get("mode").equals("receiveuuid"))
		    throw new UnsupportedOperationException(); //registerReceivingSocketUUID(inJSON);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    
    public void registerReceivingSocketParty(JSONObject inJSON) {
	try {
	    String party = (String) inJSON.get("party");
	    String uuid = inJSON.get("uuid").toString();
	    while(d.get(party) == null || d.get(party).get("uuid").equals(uuid)) {
		synchronized(d) { d.wait(); }
		System.out.println(uuid.substring(0, 3) + " awoke");
	    } System.out.println(uuid.substring(0, 3) + " broke its lock");

	    JSONObject storedJSON = d.remove(party);
	    synchronized(d) { d.notifyAll(); }
	    s.getOutputStream().write(storedJSON.toString().getBytes("UTF8"));
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void pollSendingSocketParty(JSONObject inJSON) {
	try {
	    String party = (String) inJSON.get("party");
	    String uuid = inJSON.get("uuid").toString();
	    System.out.println(uuid.substring(0, 3) + " sent new JSON.");
	    //	    while(d.get(party) != null || !d.get(party).get("uuid").equals(uuid))
	    //		synchronized(d) { d.wait(); }

	    JSONObject storedJSON = d.put(party, inJSON);
	    synchronized(d) { d.notifyAll(); }



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
