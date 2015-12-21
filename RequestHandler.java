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
    static ConcurrentHashMap<String, Object> flags = new ConcurrentHashMap<String, Object>(16, 0.9f, 2);
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
	    // System.out.println("Recieved " + inJSONRaw);
	    JSONObject inJSON = (JSONObject) p.parse(inJSONRaw);

	    if(inJSON.get("mode").equals("send") && inJSON.get("party").toString().length() != 36) // Ensure the party name is not a UUID.
		initialHandshake(inJSON);
	    else if(inJSON.get("mode").equals("send")) // Standard poll
		pollSendingSocket(inJSON);
	    else if(inJSON.get("mode").equals("receive")) // Standard Receive
		registerReceivingSocket(inJSON);
	    else throw new UnsupportedOperationException(); // Shit.
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public void initialHandshake(JSONObject inJSON) {
	try {
	    String party = (String) inJSON.get("party");
	    String uuid = inJSON.get("uuid").toString();
	    // System.out.println(uuid.substring(0, 3) + " requesting handshake as " + party);
	    JSONObject storedJSON = d.put(party, inJSON);
	    Object notifyFlag = flags.get(party);

	    if(notifyFlag == null) {
		flags.put(party, new Object());
		notifyFlag = flags.get(party);
	    }

	    if(storedJSON == null) {
		while(d.get(party) == null || d.get(party).get("uuid").equals(uuid)) {
		    synchronized(notifyFlag) { notifyFlag.wait(); }
		}
		storedJSON = d.remove(party);
	    } else {
		synchronized(flags.get(party)) { notifyFlag.notifyAll(); }
	    }

	    System.out.println(uuid.substring(0, 3) + " has paired with " + storedJSON.get("uuid").toString().substring(0, 3));
	    s.getOutputStream().write((storedJSON.toString() + "\n").getBytes("UTF8"));
	} catch (Exception e) { e.printStackTrace(); }
	run(); // Recurse to handle imminent incoming traffic on the sending socket
    }

    public void registerReceivingSocket(JSONObject inJSON) {
	try {
	    String targetUUID = (String) inJSON.get("party");
	    String localUUID = inJSON.get("uuid").toString();
	    Object localFlag = new Object();
	    flags.put(localUUID, localFlag);

	    // System.out.println(localUUID.substring(0, 3) + " registered receiving socket.");

	    while(!s.isClosed()) {
		while(d.get(localUUID) == null)
		    synchronized(localFlag) { localFlag.wait(); }

		JSONObject storedJSON = d.remove(localUUID);
		if(storedJSON.get("uuid").equals(targetUUID)) {
		    System.out.println(localUUID.substring(0, 3) + " receiving " + storedJSON.toString().substring(0, 16));
		    s.getOutputStream().write((storedJSON.toString() + "\n").getBytes("UTF8"));
		}
	    }
	} catch (Exception e) { e.printStackTrace(); }
    }

    public void pollSendingSocket(JSONObject inJSON) {
	try {
	    String targetUUID = (String) inJSON.get("party");
	    String localUUID = inJSON.get("uuid").toString();
	    // System.out.println(localUUID.substring(0, 3) + " searching for receiving socket");

	    while(flags.get(targetUUID) == null)
		Thread.sleep(1000); // Required, because send-recieve communication is one-way for simplicity
	    Object targetFlag = flags.get(targetUUID);
	    System.out.println("Sending socket of " + localUUID.substring(0, 3) + " paired with receiving socket of " + targetUUID.substring(0, 3));

	    while(!s.isClosed()) {
		String inJSONRaw = RequestHandler.readString(new InputStreamReader(s.getInputStream(), "UTF8"), 64);
		inJSON = (JSONObject) p.parse(inJSONRaw);
		System.out.println(localUUID.substring(0, 3) + " sent new JSON.");

		d.put(targetUUID, inJSON);
		synchronized(targetFlag) { targetFlag.notifyAll(); }
	    }
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
