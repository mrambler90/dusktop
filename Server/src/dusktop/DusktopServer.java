package dusktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * Il server DUSKTOP.
 * <p>
 * Server TCP multi-threaded con console
 * in grado di riconoscere comandi personalizzati.<br>
 * Progettato per essere fault-tolerant, in particolare
 * nei confronti dei problemi di connessione con i client.
 * </p>
 * @author Michele Reale
 */
public class DusktopServer {

	private ServerSocket socket;
	private LinkedList<WorkerThread> threadPool;
	
	/*
	 * Comandi accettati dal server:
	 * si possono qui definire alcuni comandi
	 * fissi (corrispondenti sempre ad una stessa
	 * stringa) per comodità. 
	 */
	public static final String ACK_MESSAGE = "1";
	
	/**
	 * Inizializza il server simulatore di DUSKTOP.
	 * @param	portNumber		Numero di porta da utilizzare.
	 * Deve essere un valore short 0-65535. Se è un riferimento
	 * <tt>null</tt> o pari a <tt>0</tt>, sarà utilizzata una
	 * porta allocata casualmente.
	 * @param	backlog			Numero massimo di connessioni
	 * simultanee gestibili dal socket (<b>backlog</b>). Se è un
	 * riferimento <tt>null</tt>, non sarà impostato alcun limite
	 * al numero di connessioni attive.
	 * @throws	IOException		Eccezione lanciata in caso di errore
	 * di sistema nell'istanziazione del socket.
	 * @throws 	IllegalArgumentException	Eccezione lanciata qualora
	 * <i>backlog</i> sia un valore intero negativo o pari a zero.
	 */
	public DusktopServer(Short portNumber, Integer backlog) throws IOException, IllegalArgumentException {
		// controllo dei parametri
		if (backlog != null && backlog <= 0)
			throw new IllegalArgumentException("Valore negativo del backlog!");
		
		// istanziazione del ServerSocket in base ai parametri passati
		try {
			if (portNumber == null && backlog == null)
				socket = new ServerSocket(0);	// porta random
			
			else if (backlog == null)
				socket = new ServerSocket((int)portNumber);
			
			else
				socket = new ServerSocket((int)portNumber, backlog);
			
			// thread pool vuoto
			threadPool = new LinkedList<WorkerThread>();
		}
		catch (IOException e) {
			// errore nell'istanziazione del socket
			throw e;
		}
	}

	/**
	 * Avvia il server, il quale inizia a servire le
	 * richieste in entrata tramite thread.
	 * <p>
	 * Una volta avviato, il server sarà gestibile 
	 * mediante il terminale, i cui comandi saranno
	 * eseguiti da un thread {@link ServerConsole}. 
	 * </p><p>
	 * Il server può essere sempre terminato inviando
	 * il comando <tt>close</tt> sul terminale, oppure
	 * premendo la combinazione di tasti Ctrl-C.
	 * </p>
	 */
	public void start() {
		
		// si avvia la console del server
		new ServerConsole().start();
		
		// intercetta la combinazione Ctrl-C per chiusura graceful del server
		// tramite un hook alla JVM
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	// utilizza il metodo serverShoutdown di un'istanza di ServerConsole
		    	new ServerConsole().serverShutdown();
		    }
		});
		
		// si avvia il ciclo di vita principale del server,
		// che sarà interrotto solo alla chiusura del socket
		// principale tramite il metodo close()
		while(true) {
			try {
	            // accetta la prima connessione in entrata, se
				// permesso dal limite del backlog
				Socket conn = socket.accept();
	            
	            System.out.println("Connessione in arrivo da "
	            		+ conn.getInetAddress() + ":" + conn.getPort() + ".");
	            
	            // istanzia un thread per gestire la nuova connessione
	            WorkerThread t = new WorkerThread(conn);
	            t.start();
			}
			catch (IOException e) {
				if (socket.isClosed())
					// è stato inviato il comando di chiusura
					System.out.println("Server terminato correttamente.");
				else
					// errore di comunicazione
					System.out.println("Errore nella comunicazione col client! " + e);
				
				return;
			}
        }
		
	}
	
	/**
	 * Thread per la gestione di una singola connessione.
	 * <p>
	 * Ciascuno di questi thread utilizza un socket ausiliario per
	 * mantenere le comunicazioni con i client: finché il client invia
	 * comandi, finché il socket non riceve un comando di chiusura o
	 * finché la connessione è attiva, il thread esegue tutti i comandi validi
	 * ricevuti dal client.<br>
	 * Nel caso in cui il client invii comandi non validi, il thread li ignora
	 * e continua a rimanere in attesa di altri comandi.
	 * </p>
	 * @author Michele Reale
	 */
	private class WorkerThread extends Thread {
		
		private Socket socket;	// il socket da utilizzare nel thread
		
		private BufferedWriter out = null;	// buffer di scrittura
		private BufferedReader in = null;	// buffer di lettura
		
		/**
		 * Istanzia il thread con il socket ausiliare.
		 * @param socket	Il socket da utilizzare per gestire la richiesta
		 * dal client.
		 * @throws IllegalArgumentException		Eccezione lanciata se
		 * il parametro è un riferimento null.
		 * @throws IOException		Eccezione lanciata qualora non sia
		 * possibile aprire correttamente i buffer di lettura e scrittura
		 * sul socket.
		 */
		private WorkerThread(Socket socket) throws IllegalArgumentException, IOException {
			// controllo parametri
			if (socket == null)
				throw new IllegalArgumentException("Riferimento null al socket!");
			
			// prepara i buffer del socket
			this.socket = socket;
			try {
				out = new BufferedWriter(new OutputStreamWriter(
					socket.getOutputStream()));
				in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			}
			catch (IOException e) {
				throw new IOException("Accesso ai buffer di input/output fallito!");
			}
			
			// aggiungi il nuovo thread al pool
			threadPool.add(this);
		}
		
		/**
		 * Chiude il socket utilizzato dal terminale per
		 * terminare l'esecuzione del thread.
		 */
		void close() {
			// chiusura non graceful del socket
			try {
				socket.close();
			}
			catch (IOException e) {}
		}
		
		/**
		 * Analizza i messaggi ricevuti dal socket client
		 * e invoca i corrispondenti metodi ausiliari per
		 * proseguire la comunicazione.
		 */
		public void run() {
			try {
				// si tiene traccia del numero di messaggi ACK
				// ricevuti dal client
				int ACKs = 0;
				
				while (true) {
					// leggi il messaggio del client
					String command = in.readLine();
					if (command == null) {
						// il client ha terminato la connessione
						throw new SocketException("Connection reset");
					}
					
					// smista il messaggio ai metodi ausiliari
					// (si può sostituire con un if-else basato su test sulle stringhe)
					switch (command) {
					
						// TODO finire tutti i comandi
						case ACK_MESSAGE:
							System.out.println("ACK " + ++ACKs + " ricevuto da "
									+ socket.getInetAddress() + ":" + socket.getPort() + ".");
							out.write("Grazie!\n"); out.flush();
							break;
						
						case "TEST":
							System.out.println("Richiesta ComandoTest da "
									+ socket.getInetAddress() + ":" + socket.getPort() + ".");
							ComandoTest();
							break;
							
						case "PROVA":
							System.out.println("Richiesta ComandoProva da "
									+ socket.getInetAddress() + ":" + socket.getPort() + ".");
							ComandoProva();
							break;
							
						default:
							// comando non riconosciuto TODO implementare a piacimento
							System.out.println("Comando non valido ricevuto da "
								+ socket.getInetAddress() + ":" + socket.getPort());
							break;
					}
				}
			}
			catch (SocketException e) {
				// controllo del caso particolare: connection reset by remote peer
				if (e.getMessage().trim().toLowerCase().compareTo("connection reset") == 0)
					System.out.println("Connessione chiusa dal client"
							+ socket.getInetAddress() + ":" + socket.getPort());
				else
					System.out.println("Errore nella comunicazione "
							+ "tra client e server: " + e.getMessage());
			}
			catch (IOException e) {
				// errore di comunicazione, tra cui
				// l'interruzione (accidentale o intenzionale)
				// della connessione TCP
				System.out.println("Errore nella comunicazione "
						+ "tra client e server: " + e.getMessage());
			}
			finally {
				// rimuove il thread dal pool, in quanto sarà chiuso
				// al termine del metodo run()
				threadPool.remove(this);
			}
		}
		
		// TODO inserire tutti i metodi ausiliari
		// TODO implementare tutti i metodi come private void senza eccezioni 
		
		/**
		 * Esegue il comando ComandoTest.
		 */
		private void ComandoTest() {
			try { 
				out.write("TEST OK\n"); out.flush();
			}
			catch (Exception e) { }
		}
		
		/**
		 * Esegue il comando ComandoProva.
		 */
		private void ComandoProva() {
			try {
				out.write("PROVA OK\n"); out.flush();
			}
			catch (Exception e) { }
		}
		
	}
		
	/**
	 * Implementazione di una console del server su terminale.
	 * <p>
	 * L'utente potrà inviare comandi al server tramite terminale,
	 * fintantoché questo thread sarà attivo. I comandi saranno
	 * tutti convertiti in forma lowercase prima dell'interpretazione.
	 * </p><p>
	 * Al momento, l'unico comando accettato è la stringa <tt>close</tt>,
	 * che causa l'interruzione di tutti i thread e la chiusura del
	 * socket server principale.
	 * </p> 
	 * @author Michele Reale
	 */
	private class ServerConsole extends Thread {
		
		/**
		 * Legge le stringhe digitate sul terminale,
		 * e tenta di eseguire i comandi corrispondenti.
		 */
		public void run() {
			
			// lo Scanner usato per leggere i comandi da terminale
			Scanner terminal = null;
			
			try {
				// lettura ciclica dei comandi dallo standard input
				terminal = new Scanner(System.in);
				
				// introduzione all'utente
				System.out.println("Server DUSKTOP attivo in locale sulla porta " + socket.getLocalPort() + ". "
						+ "Qui verranno visualizzati i messaggi "
						+ "di log delle attività e i dati ricevuti dai client.\nDigitare un comando seguito "
						+ "dal tasto Invio per inviare un comando di amministrazione al server.");
				
				while (terminal.hasNextLine()) {
					// lettura comando
					String command = terminal.nextLine();
					
					// interpretazione comando convertito in lowercase
					switch (command.toLowerCase()) {
						case "close":
							// comando di terminazione del server
							serverShutdown();
							return;
						// altri comandi implementabili
						default:
							System.out.println("Comando non riconsciuto.");
					}
				}
			}
			catch (Exception e) {
				System.out.println("Errore nell'esecuzione di un comando del "
						+ "terminale! Il server continuerà l'esecuzione: per "
						+ "terminarlo in modo graceful, utilizzare la "
						+ "combinazione di tasti Ctrl-C.");
			}
			finally {
				// chiusura dello scanner per liberare il canale standard input
				if (terminal != null)
					terminal.close();
			}
		}
		
		/**
		 * Chiude i thread ausiliari tramite i loro socket
		 * e chiude il socket principale, portando all'interruzione
		 * del ciclo di vita del server.
		 */
		private void serverShutdown() {
			
			// chiusura dei WorkerThread ausiliari
			for (WorkerThread t : threadPool)
				t.close();
				
			// chiusura del socket server principale
			try { socket.close(); }
			catch (IOException e) {
				// eventuali errori sono ignorati
			}
			
		}
	}
	
	/**
	 * Avvia un'istanza del server simulatore.
	 * <br><br>
	 * Eventuali parametri accettati sono:<br>
	 * <ol>
	 * <li>un valore intero short 0-65535 che indica il numero
	 * di porta su cui allocare il socket;</li>
	 * <li>un valore intero positivo che indica il numero massimo
	 * di connessioni simultanee che il server può servire (<b>backlog</b>);
	 * giunto a tale numero di connessioni attive, il server
	 * rifiuterà qualsiasi nuova richiesta in entrata fino a
	 * quando le connessioni attive non diminuiranno.</li>
	 * </ol>
	 * @param args		Parametri descritti come sopra.
	 */
	public static void main(String[] args) {
		
		/*
		 * controllo di eventuali parametri
		 */
		Short portNumber = null;
		Integer backlog = null;
		
		// se c'è almeno un parametro, si tenta di
		// interpretare il primo
		if (args.length >= 1) {
			try {
				portNumber = Short.parseShort(args[0]);
			}
			catch (NumberFormatException e) {
				// il parametro non è un numero intero short
				portNumber = null;
			}
		}
		
		// se ci sono almeno due parametri, si tenta di
		// interpretare il secondo (gli altri sono ignorati)
		if (args.length >= 2) {
			try {
				backlog = Integer.parseInt(args[1]);
				
				// un numero negativo o pari a zero non è
				// ammissibile come valore di backlog
				if (backlog <= 0)
					backlog = null;
			}
			catch (NumberFormatException e) {
				// il parametro non è un numero intero
				backlog = null;
			}
		}
		
		// si istanzia il DusktopServer e lo si avvia
		try {
			DusktopServer server = new DusktopServer(portNumber, backlog);
			server.start();
		}
		catch (IOException e) {
			System.out.println("Errore di connessione: " + e.getMessage());
		}
		catch (Exception e) {
			System.out.println("Errore nel server: " + e.getMessage());
		}

	}

}