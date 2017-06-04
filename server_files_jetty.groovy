import java.util.ArrayList;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

public class JettyFileServer {

	public static void main(String[] args) throws Exception {
		Server server = new Server(4499);

		ResourceHandler resourceHandler = new ResourceHandler();
		// !!!!! never set this to true even temporarily otherwise you may be
		// exposing work
		// secrets and get banned from ever working there. This almost happened
		resourceHandler.setDirectoriesListed(true);
		//resourceHandler.setWelcomeFiles(new String[] { "index.html" });

		resourceHandler.setResourceBase("/home/sarnobat");

		HandlerList handlers = new HandlerList();
		ArrayList<Handler> l = new ArrayList<Handler>();
		l.add(resourceHandler);
		l.add(new DefaultHandler());
//		Handler[] handlers2 = { null, new DefaultHandler() };
//		handlers2[0] = resourceHandler;
		handlers.setHandlers(l.toArray(new Handler[2]));
		server.setHandler(handlers);

		server.start();
		server.join();
	}

}