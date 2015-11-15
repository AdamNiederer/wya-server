import java.net.*;
import java.util.concurrent.*;
import java.io.*;
import java.util.ArrayList;

public class Server {
    
    final ServerSocket s;
    final ExecutorService e;
    static final ArrayList<Server> all = new ArrayList<Server>();
    
    public Server(int port) throws IOException {
	s = new ServerSocket(port);
	e = Executors.newFixedThreadPool(16);
    }
    
    public void run() {
	while(true) {
	    try { e.execute(new RequestHandler(s.accept())); } catch (Exception e) {}
	}
    }
    
    public void closeSocket() {
	try { s.close(); } catch (Exception e) {}
    }
    
    public static void main(String[] args) throws InterruptedException, IOException {
	try {
	    Server s = new Server(Integer.parseInt(args[0]));
	    s.run();
	} catch (Exception e) {}
    }
}
