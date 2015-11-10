import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import org.json.simple.*;
import org.json.simple.parser.*;

public class RequestHandler implements Runnable {
	
    static ConcurrentHashMap<String, String> d = new ConcurrentHashMap<String, String>(16, 0.9f, 2);
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
			System.out.println(inJSON);
			
			JSONObject j = (JSONObject) p.parse(inJSON);
			String party = (String) j.get("party");
			String storedJSON = d.put(party, j.toString());			
			if(storedJSON == null) {
				while(d.get(party).equals(j.toString()))
					synchronized(d) { d.wait(); }
				storedJSON = d.get(party);
				d.remove(party);
			} else {
				synchronized (d) { d.notifyAll(); }
			}
			s.getOutputStream().write(storedJSON.getBytes("UTF8"));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				s.close();
			} catch (Exception e) {}
		}      
    }
	
    public void closeSocket() {
		try {
			System.out.println("Closing socket...");
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
		inJSONBuilder.append(buffer, 0, i);
	} while (ir.ready());
	return inJSONBuilder.toString();
}
}
