import java.net.ServerSocket;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
