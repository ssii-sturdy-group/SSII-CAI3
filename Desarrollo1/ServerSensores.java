package SensoresReactor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

public class ServerSensores {
	
	    public Queue DatosSensores;
	    private ServerSocket server;
	    
	    public ServerSensores(String ipAddress) throws Exception {
	        if (ipAddress != null && !ipAddress.isEmpty()) 
	          this.server = new ServerSocket(0, 1, InetAddress.getByName(ipAddress));
	        else 
	          this.server = new ServerSocket(0, 1, InetAddress.getLocalHost());
	    }
	    private void listen() throws Exception {
	        String data = null;
	        Socket client = this.server.accept();
	        String clientAddress = client.getInetAddress().getHostAddress();
	        System.out.println("\r\nNuevo Sensor desde " + clientAddress);
	        
	        BufferedReader in = new BufferedReader(
	                new InputStreamReader(client.getInputStream()));        
	        while ( (data = in.readLine()) != null ) {
	            System.out.println("\r\nLectura desde " + clientAddress + ": " + data);
	            try {
	                DatosSensores.add(data);
	            }catch (IllegalStateException e) {
	                 e.printStackTrace();
	            }  
	        }
	    }
	   
	   
	    public static void main(String[] args) throws Exception {
	        Queue DatosSensores = new LinkedList(); 
		    ServerSensores app = new ServerSensores(args[0]);
	        System.out.println("\r\nEjecucanto el Servidor de Sensores: "); 
	                        
	        app.listen();
	    }
	
}
