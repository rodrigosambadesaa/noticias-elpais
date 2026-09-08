package com.example.muyinteresanteNoTocar;

import java.io.InputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;
import com.example.muyinteresante.util.RemoteRequestPolicy;

import javax.net.ssl.SSLException;

/* Parsea un canal RSS y devuelve sus items en un ArrayList */

public class DescargaNoticiasRSS extends AsyncTask<String,Integer,ArrayList<NoticiaRSS>>{

	public enum FailureKind {
		NO_NETWORK,
		HTTP_ERROR,
		FEED_UNAVAILABLE,
		NO_INTERNET,
		PARSE_ERROR
	}

	public static final class Failure {
		private final FailureKind kind;
		private final String detail;

		public Failure(FailureKind kind, String detail) {
			this.kind = kind;
			this.detail = detail;
		}

		public FailureKind getKind() { return kind; }
		public String getDetail() { return detail; }
	}

	private Context contexto=null;
	private iNoticiaRSS objetoReceptor=null;
	private ProgressDialog pd=null;
	private boolean mostrarProgreso=true;
	private boolean connectionAttemptBegun=false;
	private Failure pendingFailure;
	
	private static final String MENSAJE_PD="Descargando noticias...";
	
	
	public DescargaNoticiasRSS(Context contexto, iNoticiaRSS objetoReceptor){
		this(contexto, objetoReceptor, true);
	}

	/**
	 * Permite reutilizar el descargador para paginación/infinite scroll sin abrir
	 * un ProgressDialog modal cada vez que se solicitan noticias antiguas.
	 */
	public DescargaNoticiasRSS(Context contexto, iNoticiaRSS objetoReceptor, boolean mostrarProgreso){
		this.contexto = contexto;
		this.objetoReceptor = objetoReceptor;
		this.mostrarProgreso = mostrarProgreso;
	}


	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		// Este guard ocurre en el hilo de UI, antes de crear cualquier indicador
		// de progreso. Así una operación iniciada durante una desconexión no
		// muestra un ProgressDialog ni llega a ejecutarse en segundo plano.
		if (contexto != null && !RemoteRequestPolicy.canStartRequest(
				ConnectivityAndInternetAccess.isConnected(contexto))) {
			pendingFailure = new Failure(FailureKind.NO_NETWORK, "No hay una red utilizable.");
			Log.w("DescargaNoticiasRSS", "Descarga no iniciada: no hay red utilizable.");
			return;
		}

		if (contexto != null) {
            // Registramos el intento para que el helper multicapa pueda distinguir
            // conexión en curso de una conexión atascada.
			ConnectivityAndInternetAccess.beginConnectionAttempt(contexto);
			connectionAttemptBegun = true;
		}
		
		if (mostrarProgreso && contexto != null) {
			pd = new ProgressDialog(contexto);
			pd.setMessage(MENSAJE_PD);
			pd.setCancelable(true);
			pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					DescargaNoticiasRSS.this.cancel(true);
				}
			});
			
			pd.show();
		}
	}

	
	@Override
	protected void onCancelled() {
		super.onCancelled();
		
		// Finalizamos intento de conexión
		if (connectionAttemptBegun) {
			ConnectivityAndInternetAccess.endConnectionAttempt();
			connectionAttemptBegun = false;
		}
		
		if (pd!=null) pd.dismiss();
	}
	
	 
	@Override							// Recibe URL y nombre Canal RSS.
	protected ArrayList<NoticiaRSS> doInBackground(String... params) {

		InputStream entrada = null;
		HttpURLConnection conex = null;
		
		try{
			if (pendingFailure != null) {
				return null;
			}

			// Cheap guard only. The real RSS request below remains authoritative.
			if (contexto != null && !RemoteRequestPolicy.canStartRequest(
					ConnectivityAndInternetAccess.isConnected(contexto))) {
				pendingFailure = new Failure(FailureKind.NO_NETWORK, "No hay una red utilizable.");
				Log.w("DescargaNoticiasRSS", "Descarga omitida: no hay red utilizable.");
				return null;
			}

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setIgnoringComments(true);
			dbf.setCoalescing(true);
			DocumentBuilder db = dbf.newDocumentBuilder(); 
			
			 // Creamos objeto URL a partir de la direccion web para conectarnos con el servidor
			URL url = new URL(params[0]);
			conex = (HttpURLConnection) url.openConnection(); // Abrimos la conexion
			conex.setConnectTimeout(10000);
			conex.setReadTimeout(10000);
			conex.setUseCaches(false); // Evitamos la cache de datos.
			conex.setInstanceFollowRedirects(true);
			conex.setRequestProperty("accept", "application/rss+xml, application/xml, text/xml, */*");
			conex.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) noticias-elpais/1.3");

			int statusCode = conex.getResponseCode();
			if (RemoteRequestPolicy.classifyHttpStatus(statusCode)
					!= RemoteRequestPolicy.Outcome.SUCCESS) {
				pendingFailure = new Failure(
						FailureKind.HTTP_ERROR,
						"El feed respondió HTTP " + statusCode + ".");
				return null;
			}
			 
			 // Abrimos el fichero para su lectura/descarga
			entrada = conex.getInputStream();	

			Document arbolXML =db.parse(entrada);
			entrada.close();
			Element raiz = arbolXML.getDocumentElement(); 
			raiz.normalize(); 
			
			ArrayList<NoticiaRSS> noticias = new ArrayList<NoticiaRSS>();
			
			NodeList listaItems = raiz.getElementsByTagName("item");
			
			for (int i=0;i<listaItems.getLength();i++){
				try {
					Element item = (Element)listaItems.item(i);
					noticias.add(new NoticiaRSS(item, params[1]));
					
					publishProgress(noticias.size());
				}
				catch(Exception e){ e.printStackTrace();}
			}
			
			return noticias;
		}
		catch (Exception e){
			if (RemoteRequestPolicy.isAmbiguousConnectivityFailure(e)) {
				pendingFailure = new Failure(FailureKind.FEED_UNAVAILABLE, e.getMessage());
			} else {
				pendingFailure = new Failure(FailureKind.PARSE_ERROR, e.getMessage());
			}
			Log.w("DescargaNoticiasRSS", "Error descargando el feed", e);
			return null;
		}
		finally {
			if (entrada != null) {
				try {
					entrada.close();
				} catch (Exception ignored) { }
			}
			if (conex != null) {
				conex.disconnect();
			}
		}

	}
	
	
	@Override
	protected void onPostExecute(ArrayList<NoticiaRSS> result) {
		super.onPostExecute(result);
		
		// Finalizamos intento de conexión
		if (connectionAttemptBegun) {
			ConnectivityAndInternetAccess.endConnectionAttempt();
			connectionAttemptBegun = false;
		}
		
		if (pd!=null) pd.dismiss();
		if (result != null) {
			if (objetoReceptor!=null) objetoReceptor.onRecibeNoticiasRSS(result);
			return;
		}

		if (pendingFailure != null && pendingFailure.getKind() == FailureKind.FEED_UNAVAILABLE
				&& contexto != null) {
			// The feed failed ambiguously. Only now run the general active diagnostic.
			ConnectivityAndInternetAccess.checkInternetAsyncDefault(contexto, diagnostic -> {
				boolean generalInternetWorks = diagnostic != null && diagnostic.isReachable();
				FailureKind kind = RemoteRequestPolicy.classifyAmbiguousFailure(generalInternetWorks)
						== RemoteRequestPolicy.Outcome.FEED_UNAVAILABLE
						? FailureKind.FEED_UNAVAILABLE : FailureKind.NO_INTERNET;
				if (objetoReceptor != null) {
					objetoReceptor.onError(new Failure(kind, pendingFailure.getDetail()));
				}
			});
			return;
		}

		if (objetoReceptor != null) {
			objetoReceptor.onError(pendingFailure != null
					? pendingFailure
					: new Failure(FailureKind.PARSE_ERROR, "Respuesta RSS vacía."));
		}
	}


	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
		if (pd != null && values != null && values.length > 0) {
			pd.setMessage(MENSAJE_PD + " " + values[0]);
		}
	}
}
